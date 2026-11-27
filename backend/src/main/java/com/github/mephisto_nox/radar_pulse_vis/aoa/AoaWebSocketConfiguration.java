package com.github.mephisto_nox.radar_pulse_vis.aoa;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
public class AoaWebSocketConfiguration implements WebSocketConfigurer {
    private final AoaWebSocketHandler handler;

    public AoaWebSocketConfiguration(AoaWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/aoa");
    }
}
