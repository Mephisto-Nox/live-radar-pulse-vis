package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import com.github.mephisto_nox.radar_pulse_vis.pdw.Pdw;
import com.github.mephisto_nox.radar_pulse_vis.pdw.PdwPacketBatch;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class WaterfallAggregator {
    public static final int FREQUENCY_BIN_COUNT = 600;
    public static final int ZOOM_FREQUENCY_BIN_COUNT = 2_000;
    public static final int AMPLITUDE_BIN_COUNT = 41;
    public static final int ROW_DURATION_MILLIS = 100;
    public static final int HISTORY_SECONDS = 30;
    public static final long MIN_FREQUENCY_KHZ = 0L;
    public static final long MAX_FREQUENCY_KHZ = 60_000_000L;
    public static final int MIN_POWER_TENTH_DBM = -800;
    public static final int MAX_POWER_TENTH_DBM = -400;

    private static final int HISTORY_ROWS = HISTORY_SECONDS * 1_000 / ROW_DURATION_MILLIS;
    private static final int COUNTS_PER_ROW = FREQUENCY_BIN_COUNT * AMPLITUDE_BIN_COUNT;
    private static final int ALLOWED_LATENESS_MILLIS = ROW_DURATION_MILLIS;

    private final Map<String, TreeMap<Long, MutableRow>> pendingRows = new HashMap<>();
    private final Map<String, Deque<MutableRow>> completedRows = new LinkedHashMap<>();
    private final Map<String, Long> lastSequenceByReceiver = new HashMap<>();
    private final LongAdder lateBatches = new LongAdder();
    private final LongAdder recoveredLateBatches = new LongAdder();
    private final LongAdder expiredBatches = new LongAdder();
    private final LongAdder ignoredPulses = new LongAdder();
    private long finalizedThroughMillis = Long.MIN_VALUE;
    private long latestObservedEventTimeMillis = Long.MIN_VALUE;

    public synchronized void accept(PdwPacketBatch batch) {
        acceptBatch(batch);
    }

    public synchronized void acceptAll(List<PdwPacketBatch> batches) {
        batches.forEach(this::acceptBatch);
    }

    private void acceptBatch(PdwPacketBatch batch) {
        long receivedAtMillis = batch.receivedAt().toEpochMilli();
        latestObservedEventTimeMillis = Math.max(latestObservedEventTimeMillis, receivedAtMillis);
        long rowStartMillis = floorToRow(receivedAtMillis);
        completedRows.computeIfAbsent(batch.receiverId(), ignored -> new ArrayDeque<>());
        Long previousSequence = lastSequenceByReceiver.put(batch.receiverId(), batch.packetSequence());
        boolean sequenceGap = previousSequence != null
            && batch.packetSequence() != ((previousSequence + 1) & 0xffff_ffffL);

        MutableRow row;
        if (rowStartMillis <= finalizedThroughMillis) {
            lateBatches.increment();
            row = findCompletedRow(batch.receiverId(), rowStartMillis);
            if (row == null) {
                expiredBatches.increment();
                return;
            }
            recoveredLateBatches.increment();
        } else {
            row = pendingRows
                    .computeIfAbsent(batch.receiverId(), ignored -> new TreeMap<>())
                    .computeIfAbsent(rowStartMillis, ignored -> new MutableRow(rowStartMillis));
        }
        addBatchToRow(row, batch, sequenceGap);
    }

    public synchronized void advanceClock() {
        if (latestObservedEventTimeMillis != Long.MIN_VALUE) {
            long latestRowMaturationTime = floorToRow(latestObservedEventTimeMillis) + ROW_DURATION_MILLIS;
            advanceTo(Math.min(Instant.now().toEpochMilli(), latestRowMaturationTime));
        }
    }

    public synchronized long latestEventTimeMillis() {
        return latestObservedEventTimeMillis;
    }

    synchronized void advanceTo(long nowMillis) {
        long currentRowStart = floorToRow(nowMillis);
        long safeThrough = currentRowStart - ALLOWED_LATENESS_MILLIS;
        if (finalizedThroughMillis == Long.MIN_VALUE) {
            if (completedRows.isEmpty()) {
                return;
            }
            long firstPendingRow = pendingRows.values().stream()
                    .filter(rows -> !rows.isEmpty())
                    .mapToLong(rows -> rows.firstKey())
                    .min()
                    .orElse(safeThrough);
                    long oldestRetainedRow = safeThrough - HISTORY_ROWS * ROW_DURATION_MILLIS;
                    finalizedThroughMillis = Math.max(firstPendingRow - ROW_DURATION_MILLIS, oldestRetainedRow);
        }

        long next = finalizedThroughMillis + ROW_DURATION_MILLIS;
        while (next <= safeThrough) {
                for (Map.Entry<String, Deque<MutableRow>> receiver : completedRows.entrySet()) {
                MutableRow pending = pendingRows
                        .computeIfAbsent(receiver.getKey(), ignored -> new TreeMap<>())
                        .remove(next);
                MutableRow row = pending == null ? new MutableRow(next) : pending;
                row.trimZoomSamples();
                receiver.getValue().addLast(row);
                while (receiver.getValue().size() > HISTORY_ROWS) {
                    receiver.getValue().removeFirst();
                }
            }
            finalizedThroughMillis = next;
            next += ROW_DURATION_MILLIS;
        }

        pendingRows.values().forEach(rows -> rows.headMap(finalizedThroughMillis, true).clear());
    }

    public synchronized List<WaterfallRow> changedRowsSince(
            String receiverId,
            long fromMillis,
            Map<Long, Long> sentRevisions) {
        Deque<MutableRow> rows = completedRows.get(receiverId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<WaterfallRow> result = new ArrayList<>();
        for (MutableRow row : rows) {
            if (row.startTimeMillis >= fromMillis
                    && sentRevisions.getOrDefault(row.startTimeMillis, -1L) < row.revision) {
                result.add(row.toSnapshot());
                sentRevisions.put(row.startTimeMillis, row.revision);
            }
        }
        return result;
    }

    public synchronized List<String> receiverIds() {
        return List.copyOf(completedRows.keySet());
    }

    public Map<String, Long> metrics() {
        return Map.of(
                "lateBatches", lateBatches.sum(),
                "recoveredLateBatches", recoveredLateBatches.sum(),
                "expiredBatches", expiredBatches.sum(),
                "ignoredPulses", ignoredPulses.sum());
    }

    private MutableRow findCompletedRow(String receiverId, long rowStartMillis) {
        Deque<MutableRow> rows = completedRows.get(receiverId);
        if (rows == null) {
            return null;
        }
        for (MutableRow row : rows) {
            if (row.startTimeMillis == rowStartMillis) {
                return row;
            }
        }
        return null;
    }

    private void addBatchToRow(MutableRow row, PdwPacketBatch batch, boolean sequenceGap) {
        row.markGap(sequenceGap);
        row.addInput(batch.pdws().size());
        for (Pdw pdw : batch.pdws()) {
            if (!addPdw(row, pdw)) {
                ignoredPulses.increment();
            }
        }
        row.incrementRevision();
    }

    private boolean addPdw(MutableRow row, Pdw pdw) {
        long frequency = pdw.rfKHz();
        if (frequency < MIN_FREQUENCY_KHZ || frequency >= MAX_FREQUENCY_KHZ
                || pdw.powerTenthDbm() < MIN_POWER_TENTH_DBM) {
            return false;
        }
        int frequencyBin = (int) ((frequency - MIN_FREQUENCY_KHZ) * FREQUENCY_BIN_COUNT
                / (MAX_FREQUENCY_KHZ - MIN_FREQUENCY_KHZ));
        int amplitudeBin = Math.min(
                AMPLITUDE_BIN_COUNT - 1,
                (pdw.powerTenthDbm() - MIN_POWER_TENTH_DBM) / 10);
        row.increment(amplitudeBin * FREQUENCY_BIN_COUNT + frequencyBin);
        int zoomFrequencyBin = (int) ((frequency - MIN_FREQUENCY_KHZ) * ZOOM_FREQUENCY_BIN_COUNT
                / (MAX_FREQUENCY_KHZ - MIN_FREQUENCY_KHZ));
        row.appendZoomSample(zoomFrequencyBin * AMPLITUDE_BIN_COUNT + amplitudeBin);
        return true;
    }

    private long floorToRow(long timeMillis) {
        return Math.floorDiv(timeMillis, ROW_DURATION_MILLIS) * ROW_DURATION_MILLIS;
    }

    private static final class MutableRow {
        private final long startTimeMillis;
        private final short[] counts = new short[COUNTS_PER_ROW];
        private int[] zoomSamples = new int[64];
        private int zoomSampleCount;
        private int inputPulseCount;
        private boolean gap;
        private long revision;

        private MutableRow(long startTimeMillis) {
            this.startTimeMillis = startTimeMillis;
        }

        private void addInput(int count) {
            inputPulseCount = saturatingAdd(inputPulseCount, count);
        }

        private void increment(int index) {
            int current = Short.toUnsignedInt(counts[index]);
            if (current < 0xffff) {
                counts[index] = (short) (current + 1);
            }
        }

        private void markGap(boolean hasGap) {
            gap |= hasGap;
        }

        private void incrementRevision() {
            revision++;
        }

        private void appendZoomSample(int packedSample) {
            if (zoomSampleCount == zoomSamples.length) {
                zoomSamples = java.util.Arrays.copyOf(zoomSamples, Math.max(64, zoomSamples.length * 2));
            }
            zoomSamples[zoomSampleCount++] = packedSample;
        }

        private void trimZoomSamples() {
            if (zoomSamples.length != zoomSampleCount) {
                zoomSamples = java.util.Arrays.copyOf(zoomSamples, zoomSampleCount);
            }
        }

        private WaterfallRow toSnapshot() {
            return new WaterfallRow(startTimeMillis, inputPulseCount, gap, revision, counts.clone(),
                    java.util.Arrays.copyOf(zoomSamples, zoomSampleCount));
        }

        private int saturatingAdd(int left, int right) {
            long sum = (long) left + right;
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }
    }
}
