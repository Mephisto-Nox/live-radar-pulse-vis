package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
public class WaterfallWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(WaterfallWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 500;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;
    private static final int DEFAULT_WINDOW_SECONDS = 10;
    private static final int MIN_WINDOW_SECONDS = 5;
    private static final int MAX_WINDOW_SECONDS = 30;
    private static final long ROW_DURATION_MILLIS = WaterfallAggregator.ROW_DURATION_MILLIS;

    private final WaterfallAggregator aggregator;
    private final WaterfallTileEncoder encoder;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ClientState> sessions = new ConcurrentHashMap<>();
    private volatile List<String> lastReceiverList = List.of();

    public WaterfallWebSocketHandler(
            WaterfallAggregator aggregator,
            WaterfallTileEncoder encoder,
            ObjectMapper objectMapper) {
        this.aggregator = aggregator;
        this.encoder = encoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
        sessions.put(session.getId(), new ClientState(decorated));
        sendReceiverStatus(decorated);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            ClientCommand command = objectMapper.readValue(message.getPayload(), ClientCommand.class);
            if (!"configure".equals(command.type())) {
                return;
            }
            ClientState state = sessions.get(session.getId());
            if (state == null) {
                return;
            }
            String receiverId = command.receiverId();
            if (receiverId == null || receiverId.isBlank()) {
                receiverId = "RX-01";
            }
            int windowSeconds = command.timeWindowSeconds() == null
                    ? DEFAULT_WINDOW_SECONDS
                    : Math.clamp(command.timeWindowSeconds(), MIN_WINDOW_SECONDS, MAX_WINDOW_SECONDS);
            double frequencyFromGHz = command.frequencyFromGHz() == null || !Double.isFinite(command.frequencyFromGHz())
                    ? 0.0
                    : Math.clamp(command.frequencyFromGHz(), 0.0, 60.0);
            double frequencyToGHz = command.frequencyToGHz() == null || !Double.isFinite(command.frequencyToGHz())
                    ? 60.0
                    : Math.clamp(command.frequencyToGHz(), 0.0, 60.0);
            if (frequencyToGHz <= frequencyFromGHz) {
                frequencyFromGHz = 0.0;
                frequencyToGHz = 60.0;
            }
            int sectorStart = toHundredthDegree(command.angleSectorFromDeg());
            int sectorEnd = toHundredthDegree(command.angleSectorToDeg());
            boolean sectorEnabled = Boolean.TRUE.equals(command.angleSectorEnabled()) && sectorStart != sectorEnd;
            synchronized (state) {
                state.receiverId = receiverId;
                state.timeWindowSeconds = windowSeconds;
                state.frequencyFromKHz = Math.round(frequencyFromGHz * 1_000_000.0);
                state.frequencyToKHz = Math.round(frequencyToGHz * 1_000_000.0);
                state.zoomed = frequencyFromGHz > 0.0 || frequencyToGHz < 60.0;
                state.angleSectorEnabled = sectorEnabled;
                state.angleSectorFromHundredthDegree = sectorStart;
                state.angleSectorToHundredthDegree = sectorEnd;
                state.sentRevisions.clear();
            }
            sendReceiverStatus(state.session);
        } catch (RuntimeException exception) {
            logger.debug("Ignored malformed waterfall WebSocket command", exception);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        logger.debug("Removed waterfall WebSocket client after transport error", exception);
    }

    @Scheduled(fixedRate = WaterfallAggregator.ROW_DURATION_MILLIS)
    public void sendAvailableRows() {
        aggregator.advanceClock();
        List<String> receiverIds = aggregator.receiverIds();
        if (!receiverIds.equals(lastReceiverList)) {
            lastReceiverList = receiverIds;
            sessions.values().forEach(state -> sendReceiverStatus(state.session));
        }

        long now = aggregator.latestEventTimeMillis();
        if (now == Long.MIN_VALUE) {
            return;
        }
        sessions.forEach((sessionId, state) -> sendRows(sessionId, state, now));
    }

    private void sendRows(String sessionId, ClientState state, long nowMillis) {
        synchronized (state) {
            if (state.receiverId == null || !state.session.isOpen()) {
                return;
            }
            long earliestVisible = nowMillis - state.timeWindowSeconds * 1_000L;
            for (WaterfallRow row : aggregator.changedRowsSince(state.receiverId, earliestVisible, state.sentRevisions)) {
                try {
                state.session.sendMessage(new BinaryMessage(encoder.encode(
                        state.receiverId,
                        row,
                        state.frequencyFromKHz,
                        state.frequencyToKHz,
                        state.zoomed,
                        state.angleSectorEnabled,
                        state.angleSectorFromHundredthDegree,
                        state.angleSectorToHundredthDegree)));
                } catch (IOException | RuntimeException exception) {
                    sessions.remove(sessionId, state);
                    closeSession(state.session);
                    logger.debug("Removed slow waterfall WebSocket client", exception);
                    return;
                }
            }
            state.sentRevisions.keySet().removeIf(startMillis -> startMillis < earliestVisible);
        }
    }

    private void sendReceiverStatus(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of("type", "status", "receivers", aggregator.receiverIds(), "defaultReceiverId", "RX-01"))));
        } catch (IOException | RuntimeException exception) {
            sessions.remove(session.getId());
            closeSession(session);
            logger.debug("Could not send waterfall status to WebSocket client", exception);
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException exception) {
            logger.debug("Could not close waterfall WebSocket session", exception);
        }
    }

    private int toHundredthDegree(Double degrees) {
        if (degrees == null || !Double.isFinite(degrees)) return 0;
        return Math.clamp((int) Math.round(degrees * 100.0), 0, 36_000);
    }

    private record ClientCommand(
            String type,
            String receiverId,
            Integer timeWindowSeconds,
            Double frequencyFromGHz,
            Double frequencyToGHz,
            Boolean angleSectorEnabled,
            Double angleSectorFromDeg,
            Double angleSectorToDeg) {}

    private static final class ClientState {
        private final WebSocketSession session;
        private String receiverId;
        private int timeWindowSeconds = DEFAULT_WINDOW_SECONDS;
        private long frequencyFromKHz = WaterfallAggregator.MIN_FREQUENCY_KHZ;
        private long frequencyToKHz = WaterfallAggregator.MAX_FREQUENCY_KHZ;
        private boolean zoomed;
        private boolean angleSectorEnabled;
        private int angleSectorFromHundredthDegree;
        private int angleSectorToHundredthDegree;
        private final Map<Long, Long> sentRevisions = new HashMap<>();

        private ClientState(WebSocketSession session) {
            this.session = session;
        }
    }
}
