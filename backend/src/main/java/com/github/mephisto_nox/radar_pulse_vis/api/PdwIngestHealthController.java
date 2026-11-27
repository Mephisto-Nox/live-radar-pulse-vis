package com.github.mephisto_nox.radar_pulse_vis.api;

import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwIngestMetrics;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwKafkaPublisher;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwUdpReceiver;
import com.github.mephisto_nox.radar_pulse_vis.waterfall.WaterfallAggregator;
import com.github.mephisto_nox.radar_pulse_vis.waterfall.WaterfallKafkaConsumer;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PdwIngestHealthController {
    private final PdwUdpReceiver udpReceiver;
    private final PdwKafkaPublisher kafkaPublisher;
    private final PdwIngestMetrics metrics;
    private final WaterfallKafkaConsumer waterfallConsumer;
    private final WaterfallAggregator waterfallAggregator;

    public PdwIngestHealthController(
            PdwUdpReceiver udpReceiver,
            PdwKafkaPublisher kafkaPublisher,
            PdwIngestMetrics metrics,
            WaterfallKafkaConsumer waterfallConsumer,
            WaterfallAggregator waterfallAggregator) {
        this.udpReceiver = udpReceiver;
        this.kafkaPublisher = kafkaPublisher;
        this.metrics = metrics;
        this.waterfallConsumer = waterfallConsumer;
        this.waterfallAggregator = waterfallAggregator;
    }

    @GetMapping("/api/pdw/health")
    public Map<String, Object> status() {
        return Map.of(
                "udpReceiver", udpReceiver.isRunning() ? "UP" : "DOWN",
                "kafkaPublisherWorker", kafkaPublisher.isRunning() ? "UP" : "DOWN",
                "metrics", metrics.snapshot(),
                "waterfallConsumer", Map.of(
                    "consumedBatches", waterfallConsumer.consumedBatchCount(),
                    "invalidBatches", waterfallConsumer.invalidBatchCount(),
                        "emptyPolls", waterfallConsumer.emptyPollCount(),
                    "aggregation", waterfallAggregator.metrics()));
    }
}
