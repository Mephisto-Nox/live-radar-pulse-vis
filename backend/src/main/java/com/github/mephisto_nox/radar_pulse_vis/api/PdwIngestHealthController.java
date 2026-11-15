package com.github.mephisto_nox.radar_pulse_vis.api;

import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwIngestMetrics;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwKafkaPublisher;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwUdpReceiver;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PdwIngestHealthController {
    private final PdwUdpReceiver udpReceiver;
    private final PdwKafkaPublisher kafkaPublisher;
    private final PdwIngestMetrics metrics;

    public PdwIngestHealthController(
            PdwUdpReceiver udpReceiver,
            PdwKafkaPublisher kafkaPublisher,
            PdwIngestMetrics metrics) {
        this.udpReceiver = udpReceiver;
        this.kafkaPublisher = kafkaPublisher;
        this.metrics = metrics;
    }

    @GetMapping("/api/pdw/health")
    public Map<String, Object> status() {
        return Map.of(
                "udpReceiver", udpReceiver.isRunning() ? "UP" : "DOWN",
                "kafkaPublisherWorker", kafkaPublisher.isRunning() ? "UP" : "DOWN",
                "metrics", metrics.snapshot());
    }
}
