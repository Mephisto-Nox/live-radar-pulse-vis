package com.github.mephisto_nox.radar_pulse_vis.aoa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

@Component
public class AoaWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(AoaWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 500;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;
    private static final int DEFAULT_WINDOW_SECONDS = 10;
    private static final int MIN_WINDOW_SECONDS = 5;
    private static final int MAX_WINDOW_SECONDS = AoaAggregator.HISTORY_SECONDS;
    private static final int MAX_POINTS_PER_WINDOW = 3_000;

    private final AoaAggregator aggregator;
    private final AoaTileEncoder encoder;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ClientState> sessions = new ConcurrentHashMap<>();
    private volatile List<String> lastReceiverList = List.of();

    public AoaWebSocketHandler(AoaAggregator aggregator, AoaTileEncoder encoder, ObjectMapper objectMapper) {
        this.aggregator = aggregator;
        this.encoder = encoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
        ClientState state = new ClientState(decorated);
        sessions.put(session.getId(), state);
        sendReceiverStatus(decorated);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            ClientCommand command = objectMapper.readValue(message.getPayload(), ClientCommand.class);
            if (!"configure".equals(command.type())) return;
            ClientState state = sessions.get(session.getId());
            if (state == null) return;

            List<String> receiverIds = normalizeReceiverIds(command.receiverIds(), aggregator.receiverIds());
            int windowSeconds = command.timeWindowSeconds() == null
                    ? DEFAULT_WINDOW_SECONDS
                    : Math.clamp(command.timeWindowSeconds(), MIN_WINDOW_SECONDS, MAX_WINDOW_SECONDS);
            double minFrequencyGHz = finiteOr(command.frequencyFromGHz(), 0.0);
            double maxFrequencyGHz = finiteOr(command.frequencyToGHz(), 60.0);
            minFrequencyGHz = Math.clamp(minFrequencyGHz, 0.0, 60.0);
            maxFrequencyGHz = Math.clamp(maxFrequencyGHz, 0.0, 60.0);
            if (maxFrequencyGHz <= minFrequencyGHz) {
                minFrequencyGHz = 0.0;
                maxFrequencyGHz = 60.0;
            }
            int minAmplitude = toTenthDbm(finiteOr(command.minAmplitudeDbm(), -80.0));
            int maxAmplitude = toTenthDbm(finiteOr(command.maxAmplitudeDbm(), -30.0));
            if (maxAmplitude <= minAmplitude) {
                minAmplitude = -800;
                maxAmplitude = -300;
            }
            int threshold = toTenthDbm(finiteOr(command.amplitudeThresholdDbm(), -80.0));
            AoaRadiusMode radiusMode = AoaRadiusMode.fromCommand(command.radiusMode());
            int visibleRows = windowSeconds * 1_000 / AoaAggregator.ROW_DURATION_MILLIS;
            int sampleLimit = MAX_POINTS_PER_WINDOW / (visibleRows * Math.max(1, receiverIds.size()));
            AoaView view = new AoaView(
                    Math.round(minFrequencyGHz * 1_000_000.0),
                    Math.round(maxFrequencyGHz * 1_000_000.0),
                    threshold,
                    minAmplitude,
                    maxAmplitude,
                    windowSeconds * 1_000,
                    sampleLimit,
                    radiusMode);
            synchronized (state) {
                state.receiverIds = receiverIds;
                state.timeWindowSeconds = windowSeconds;
                state.view = view;
                state.sentRevisions.clear();
            }
            sendReceiverStatus(state.session);
        } catch (RuntimeException exception) {
            logger.debug("Ignored malformed AOA WebSocket command", exception);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        logger.debug("Removed AOA WebSocket client after transport error", exception);
    }

    @Scheduled(fixedRate = AoaAggregator.ROW_DURATION_MILLIS)
    public void sendAvailableRows() {
        aggregator.advanceClock();
        List<String> receiverIds = aggregator.receiverIds();
        if (!receiverIds.equals(lastReceiverList)) {
            lastReceiverList = receiverIds;
            sessions.values().forEach(state -> sendReceiverStatus(state.session));
        }
        long now = System.currentTimeMillis();
        sessions.forEach((sessionId, state) -> sendRows(sessionId, state, now));
    }

    private void sendRows(String sessionId, ClientState state, long nowMillis) {
        synchronized (state) {
            if (state.view == null || !state.session.isOpen()) return;
            long earliestVisible = nowMillis - state.timeWindowSeconds * 1_000L;
            for (String receiverId : state.receiverIds) {
                Map<Long, Long> revisions = state.sentRevisions.computeIfAbsent(receiverId, ignored -> new HashMap<>());
                for (AoaRow row : aggregator.changedRowsSince(receiverId, earliestVisible, revisions)) {
                    try {
                        state.session.sendMessage(new BinaryMessage(encoder.encode(row, state.view, nowMillis)));
                    } catch (IOException | RuntimeException exception) {
                        sessions.remove(sessionId, state);
                        closeSession(state.session);
                        logger.debug("Removed slow AOA WebSocket client", exception);
                        return;
                    }
                }
                revisions.keySet().removeIf(startMillis -> startMillis < earliestVisible);
            }
        }
    }

    private void sendReceiverStatus(WebSocketSession session) {
        try {
            List<String> receivers = aggregator.receiverIds();
            String defaultReceiverId = receivers.isEmpty() ? "RX-01" : receivers.getFirst();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "status",
                    "receivers", receivers,
                    "defaultReceiverId", defaultReceiverId))));
        } catch (IOException | RuntimeException exception) {
            sessions.remove(session.getId());
            closeSession(session);
            logger.debug("Could not send AOA receiver status", exception);
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException exception) {
            logger.debug("Could not close AOA WebSocket session", exception);
        }
    }

    private List<String> normalizeReceiverIds(List<String> requested, List<String> available) {
        if (requested == null || requested.isEmpty()) {
            return available.isEmpty() ? List.of("RX-01") : List.of(available.getFirst());
        }
        List<String> distinct = new ArrayList<>();
        for (String receiverId : requested) {
            if (receiverId != null && available.contains(receiverId) && !distinct.contains(receiverId)) {
                distinct.add(receiverId);
            }
            if (distinct.size() == 16) break;
        }
        return distinct.isEmpty() ? List.of("RX-01") : List.copyOf(distinct);
    }

    private double finiteOr(Double value, double fallback) {
        return value == null || !Double.isFinite(value) ? fallback : value;
    }

    private int toTenthDbm(double value) {
        return Math.clamp((int) Math.round(value * 10.0), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private record ClientCommand(
            String type,
            List<String> receiverIds,
            Integer timeWindowSeconds,
            Double frequencyFromGHz,
            Double frequencyToGHz,
            Double amplitudeThresholdDbm,
            Double minAmplitudeDbm,
            Double maxAmplitudeDbm,
            String radiusMode) {}

    private static final class ClientState {
        private final WebSocketSession session;
        private List<String> receiverIds = List.of();
        private int timeWindowSeconds = DEFAULT_WINDOW_SECONDS;
        private AoaView view;
        private final Map<String, Map<Long, Long>> sentRevisions = new HashMap<>();

        private ClientState(WebSocketSession session) {
            this.session = session;
        }
    }
}
