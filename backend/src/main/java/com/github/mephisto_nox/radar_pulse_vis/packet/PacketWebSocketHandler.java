package com.github.mephisto_nox.radar_pulse_vis.packet;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PacketWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(PacketWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 1_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final PacketSubscription subscription;

    public PacketWebSocketHandler(PacketChannel packetChannel, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.subscription = packetChannel.subscribe(this::broadcast);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        logger.debug("Packet WebSocket connection closed after transport error", exception);
    }

    @PreDestroy
    void closeSubscription() {
        subscription.close();
    }

    private void broadcast(ReceivedPacket packet) {
        final TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(packet));
        } catch (JacksonException exception) {
            logger.error("Could not serialize received packet metadata", exception);
            return;
        }

        sessions.forEach((sessionId, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(sessionId, session);
                }
            } catch (IOException | RuntimeException exception) {
                sessions.remove(sessionId, session);
                closeSession(session);
                logger.debug("Removed packet WebSocket client after send failure", exception);
            }
        });
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException | RuntimeException exception) {
            logger.debug("Could not close packet WebSocket session", exception);
        }
    }
}
