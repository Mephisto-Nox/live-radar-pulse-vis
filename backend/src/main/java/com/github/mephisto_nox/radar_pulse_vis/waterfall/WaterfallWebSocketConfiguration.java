package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WaterfallWebSocketConfiguration implements WebSocketConfigurer {
    private final WaterfallWebSocketHandler handler;

    public WaterfallWebSocketConfiguration(WaterfallWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/waterfall");
    }
}
