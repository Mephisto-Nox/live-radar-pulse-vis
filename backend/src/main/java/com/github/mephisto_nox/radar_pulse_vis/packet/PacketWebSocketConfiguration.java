package com.github.mephisto_nox.radar_pulse_vis.packet;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PacketWebSocketConfiguration implements WebSocketConfigurer {
    private final PacketWebSocketHandler packetWebSocketHandler;

    public PacketWebSocketConfiguration(PacketWebSocketHandler packetWebSocketHandler) {
        this.packetWebSocketHandler = packetWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(packetWebSocketHandler, "/ws/packets");
    }
}
