package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.mephisto_nox.radar_pulse_vis.pdw.Pdw;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwPacketBatch;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaterfallAggregatorTest {
    private final WaterfallAggregator aggregator = new WaterfallAggregator();

    @Test
    void aggregatesFrequencyAndAmplitudeIntoCorrectBins() {
        long rowStart = 1_000_000L;
        aggregator.accept(batch("RX-01", rowStart + 20,
                new Pdw(10, 8_000_000, 10, -650, 100),
                new Pdw(20, 8_120_000, 10, -641, 200),
                new Pdw(30, 9_000_000, 10, -400, 300)));

        aggregator.advanceTo(rowStart + WaterfallAggregator.ROW_DURATION_MILLIS * 2L);

        WaterfallRow row = rowsSince("RX-01", rowStart).getFirst();
        assertThat(row.gap()).isFalse();
        int lowFrequencyBin = frequencyBin(8_000_000);
        int nextFrequencyBin = frequencyBin(8_120_000);
        int highFrequencyBin = frequencyBin(9_000_000);
        int lowAmplitudeBin = 15 * WaterfallAggregator.FREQUENCY_BIN_COUNT + lowFrequencyBin;
        int nextAmplitudeBin = 15 * WaterfallAggregator.FREQUENCY_BIN_COUNT + nextFrequencyBin;
        int highAmplitudeBin = 40 * WaterfallAggregator.FREQUENCY_BIN_COUNT + highFrequencyBin;
        assertThat(row.inputPulseCount()).isEqualTo(3);
        assertThat(Short.toUnsignedInt(row.countsByAmplitudeAndFrequency()[lowAmplitudeBin])).isEqualTo(1);
        assertThat(Short.toUnsignedInt(row.countsByAmplitudeAndFrequency()[nextAmplitudeBin])).isEqualTo(1);
        assertThat(Short.toUnsignedInt(row.countsByAmplitudeAndFrequency()[highAmplitudeBin])).isEqualTo(1);
    }

    @Test
    void createsExplicitEmptyRowsForMissingTimeSlices() {
        long rowStart = 1_000_000L;
        aggregator.accept(batch("RX-01", rowStart + 20, new Pdw(10, 8_000_000, 10, -650, 100)));

        aggregator.advanceTo(rowStart + WaterfallAggregator.ROW_DURATION_MILLIS * 3L);

        List<WaterfallRow> rows = rowsSince("RX-01", rowStart);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).inputPulseCount()).isZero();
        assertThat(rows.get(2).inputPulseCount()).isZero();
        assertThat(rows.get(1).gap()).isFalse();
    }

    @Test
    void ignoresPulsesOutsideConfiguredFrequencyOrAmplitudeGrid() {
        long rowStart = 1_000_000L;
        aggregator.accept(batch("RX-01", rowStart + 20,
                new Pdw(10, 60_000_000, 10, -650, 100),
                new Pdw(20, 8_000_000, 10, -801, 100)));

        aggregator.advanceTo(rowStart + WaterfallAggregator.ROW_DURATION_MILLIS * 2L);

        WaterfallRow row = rowsSince("RX-01", rowStart).getFirst();
        assertThat(row.inputPulseCount()).isEqualTo(2);
        assertThat(row.countsByAmplitudeAndFrequency()).containsOnly((short) 0);
        assertThat(aggregator.metrics()).containsEntry("ignoredPulses", 2L);
    }

    @Test
    void marksSequenceLossAsGap() {
        long rowStart = 1_000_000L;
        aggregator.accept(batch("RX-01", rowStart + 20, 1, new Pdw(10, 8_000_000, 10, -650, 100)));
        aggregator.accept(batch("RX-01", rowStart + 40, 3, new Pdw(20, 8_000_000, 10, -650, 100)));

        aggregator.advanceTo(rowStart + WaterfallAggregator.ROW_DURATION_MILLIS * 2L);

        assertThat(rowsSince("RX-01", rowStart).getFirst().gap()).isTrue();
    }

    @Test
    void incorporatesLateBatchIntoRetainedRowAndPublishesNewRevision() {
        long rowStart = 1_000_000L;
        aggregator.accept(batch("RX-01", rowStart + 20, 1, new Pdw(10, 8_000_000, 10, -650, 100)));
        aggregator.advanceTo(rowStart + WaterfallAggregator.ROW_DURATION_MILLIS * 2L);
        Map<Long, Long> sentRevisions = new HashMap<>();
        WaterfallRow firstRevision = aggregator.changedRowsSince("RX-01", rowStart, sentRevisions).getFirst();

        aggregator.accept(batch("RX-01", rowStart + 40, 2, new Pdw(20, 8_020_000, 10, -641, 200)));

        WaterfallRow correctedRow = aggregator.changedRowsSince("RX-01", rowStart, sentRevisions).getFirst();
        assertThat(correctedRow.revision()).isGreaterThan(firstRevision.revision());
        assertThat(correctedRow.inputPulseCount()).isEqualTo(2);
        assertThat(aggregator.metrics()).containsEntry("recoveredLateBatches", 1L);
    }

    @Test
    void countsLateBatchesOutsideTheRetainedWindowAsExpired() {
        long rowStart = 1_000_000L;
        aggregator.accept(batch("RX-01", rowStart + 20, 1, new Pdw(10, 8_000_000, 10, -650, 100)));
        aggregator.advanceTo(rowStart + 40_000);

        aggregator.accept(batch("RX-01", rowStart + 40, 2, new Pdw(20, 8_020_000, 10, -641, 200)));

        assertThat(aggregator.metrics()).containsEntry("expiredBatches", 1L);
    }

    @Test
    void finalizesLastEventTimeRowEvenWhenNoNewBatchArrives() {
        long receivedAtMillis = Instant.now().minusSeconds(1).toEpochMilli();
        long rowStart = Math.floorDiv(receivedAtMillis, WaterfallAggregator.ROW_DURATION_MILLIS)
                * WaterfallAggregator.ROW_DURATION_MILLIS;
        aggregator.accept(batch("RX-01", receivedAtMillis, new Pdw(10, 8_000_000, 10, -650, 100)));

        aggregator.advanceClock();

        assertThat(rowsSince("RX-01", rowStart)).hasSize(1);
    }

    private PdwPacketBatch batch(String receiverId, long receivedAtMillis, Pdw... pdws) {
        return batch(receiverId, receivedAtMillis, 1, pdws);
    }

    private PdwPacketBatch batch(String receiverId, long receivedAtMillis, long sequence, Pdw... pdws) {
        return new PdwPacketBatch(receiverId, sequence, 0,
                Instant.ofEpochMilli(receivedAtMillis), List.of(pdws));
    }

    private List<WaterfallRow> rowsSince(String receiverId, long fromMillis) {
        return aggregator.changedRowsSince(receiverId, fromMillis, new HashMap<>());
    }

    private int frequencyBin(long frequencyKHz) {
        return (int) ((frequencyKHz - WaterfallAggregator.MIN_FREQUENCY_KHZ)
                * WaterfallAggregator.FREQUENCY_BIN_COUNT
                / (WaterfallAggregator.MAX_FREQUENCY_KHZ - WaterfallAggregator.MIN_FREQUENCY_KHZ));
    }
}
