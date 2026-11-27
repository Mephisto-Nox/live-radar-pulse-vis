package com.github.mephisto_nox.radar_pulse_vis.api;

import com.github.mephisto_nox.radar_pulse_vis.packet.PacketPipeline;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final PacketPipeline packetPipeline;

    public HealthController(PacketPipeline packetPipeline) {
        this.packetPipeline = packetPipeline;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        String pipelineStatus = packetPipeline.isRunning() ? "UP" : "DOWN";
        return Map.of(
                "status", "UP",
                "service", "pulse-vision-api",
                "packetPipeline", pipelineStatus);
    }
}