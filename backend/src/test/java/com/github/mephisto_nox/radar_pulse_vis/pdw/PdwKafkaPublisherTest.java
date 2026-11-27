package com.github.mephisto_nox.radar_pulse_vis.pdw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

class PdwKafkaPublisherTest {
    @Test
    void dropsNewestBatchAndCountsItWhenBoundedQueueIsFull() {
        PdwIngestMetrics metrics = new PdwIngestMetrics();
        PdwKafkaPublisher publisher = new PdwKafkaPublisher(
                mock(KafkaTemplate.class), new ObjectMapper(), metrics, "pdw-waterfall-v1", 1);
        PdwPacketBatch batch = new PdwPacketBatch("RX-01", 1, 100, Instant.now(),
                List.of(new Pdw(100_000, 9_410_000, 100, -350, 1_234)));

        publisher.publish(batch);
        publisher.publish(batch);

        assertThat(metrics.snapshot()).containsEntry("droppedBatches", 1L);
    }
}
