package com.github.mephisto_nox.radar_pulse_vis.aoa;

import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwPacketBatch;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AoaKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(AoaKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final AoaAggregator aggregator;

    public AoaKafkaConsumer(ObjectMapper objectMapper, AoaAggregator aggregator) {
        this.objectMapper = objectMapper;
        this.aggregator = aggregator;
    }

    @KafkaListener(topics = "${pulsevision.kafka.topic}", groupId = "${pulsevision.kafka.aoa-consumer-group}")
    public void consume(List<String> messages) {
        if (messages.isEmpty()) return;
        List<PdwPacketBatch> batches = new ArrayList<>(messages.size());
        for (String message : messages) {
            try {
                batches.add(objectMapper.readValue(message, PdwPacketBatch.class));
            } catch (RuntimeException exception) {
                logger.warn("Discarded malformed Kafka batch in AOA consumer", exception);
            }
        }
        if (!batches.isEmpty()) aggregator.acceptAll(batches);
    }
}
