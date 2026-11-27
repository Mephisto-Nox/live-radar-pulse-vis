package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.mephisto_nox.radar_pulse_vis.pdw.Pdw;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwPacketBatch;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WaterfallKafkaConsumerTest {
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WaterfallAggregator aggregator;

    @Test
    void deserializesAndForwardsKafkaBatchToAggregator() {
        PdwPacketBatch batch = new PdwPacketBatch("RX-01", 1, 100, Instant.parse("2026-11-15T10:00:00Z"),
                List.of(new Pdw(100_000, 9_410_000, 100, -350, 1_234)));
        WaterfallKafkaConsumer consumer = new WaterfallKafkaConsumer(objectMapper, aggregator);
        when(objectMapper.readValue("batch-json", PdwPacketBatch.class)).thenReturn(batch);

        consumer.consume(List.of("batch-json"));

        verify(aggregator).acceptAll(List.of(batch));
        assertThat(consumer.consumedBatchCount()).isEqualTo(1);
        assertThat(consumer.invalidBatchCount()).isZero();
    }

    @Test
    void countsMalformedKafkaBatchWithoutForwardingIt() {
        WaterfallKafkaConsumer consumer = new WaterfallKafkaConsumer(objectMapper, aggregator);
        when(objectMapper.readValue("invalid", PdwPacketBatch.class)).thenThrow(new IllegalArgumentException("invalid"));

        consumer.consume(List.of("invalid"));

        assertThat(consumer.consumedBatchCount()).isZero();
        assertThat(consumer.invalidBatchCount()).isEqualTo(1);
    }

    @Test
    void aggregatesAllValidRecordsFromOneKafkaPollTogether() {
        PdwPacketBatch batch = new PdwPacketBatch("RX-01", 1, 100, Instant.parse("2026-11-15T10:00:00Z"),
                List.of(new Pdw(100_000, 9_410_000, 100, -350, 1_234)));
        WaterfallKafkaConsumer consumer = new WaterfallKafkaConsumer(objectMapper, aggregator);
        when(objectMapper.readValue("one", PdwPacketBatch.class)).thenReturn(batch);
        when(objectMapper.readValue("two", PdwPacketBatch.class)).thenReturn(batch);

        consumer.consume(List.of("one", "two"));

        verify(aggregator).acceptAll(List.of(batch, batch));
        assertThat(consumer.consumedBatchCount()).isEqualTo(2);
    }
}
