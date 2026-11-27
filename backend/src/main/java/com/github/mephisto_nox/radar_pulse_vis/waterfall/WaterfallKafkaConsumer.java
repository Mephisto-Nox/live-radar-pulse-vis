package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwPacketBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class WaterfallKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(WaterfallKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final WaterfallAggregator aggregator;
    private final LongAdder consumedBatches = new LongAdder();
    private final LongAdder invalidBatches = new LongAdder();
    private final LongAdder emptyPolls = new LongAdder();

    public WaterfallKafkaConsumer(ObjectMapper objectMapper, WaterfallAggregator aggregator) {
        this.objectMapper = objectMapper;
        this.aggregator = aggregator;
    }

    @KafkaListener(topics = "${pulsevision.kafka.topic}", groupId = "${pulsevision.kafka.waterfall-consumer-group}")
    public void consume(List<String> messages) {
        if (messages.isEmpty()) {
            emptyPolls.increment();
            return;
        }
        List<PdwPacketBatch> batches = new ArrayList<>(messages.size());
        for (String message : messages) {
            try {
                batches.add(objectMapper.readValue(message, PdwPacketBatch.class));
            } catch (RuntimeException exception) {
                invalidBatches.increment();
                logger.warn("Discarded malformed Kafka PDW batch", exception);
            }
        }
        if (!batches.isEmpty()) {
            aggregator.acceptAll(batches);
            consumedBatches.add(batches.size());
        }
    }

    public long consumedBatchCount() {
        return consumedBatches.sum();
    }

    public long invalidBatchCount() {
        return invalidBatches.sum();
    }

    public long emptyPollCount() {
        return emptyPolls.sum();
    }
}
