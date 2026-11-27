package com.github.mephisto_nox.radar_pulse_vis.aoa;

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
import org.springframework.stereotype.Component;

@Component
public class AoaAggregator {
    public static final int ROW_DURATION_MILLIS = 100;
    public static final int HISTORY_SECONDS = 30;
    public static final long MIN_FREQUENCY_KHZ = 0L;
    public static final long MAX_FREQUENCY_KHZ = 60_000_000L;

    private static final int HISTORY_ROWS = HISTORY_SECONDS * 1_000 / ROW_DURATION_MILLIS;
    private static final int ALLOWED_LATENESS_MILLIS = ROW_DURATION_MILLIS;

    private final Map<String, TreeMap<Long, MutableRow>> pendingRows = new HashMap<>();
    private final Map<String, Deque<MutableRow>> completedRows = new LinkedHashMap<>();
    private final Map<String, Long> lastSequenceByReceiver = new HashMap<>();
    private long finalizedThroughMillis = Long.MIN_VALUE;
    private long latestObservedEventTimeMillis = Long.MIN_VALUE;

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
            row = findCompletedRow(batch.receiverId(), rowStartMillis);
            if (row == null) {
                return;
            }
        } else {
            row = pendingRows.computeIfAbsent(batch.receiverId(), ignored -> new TreeMap<>())
                    .computeIfAbsent(rowStartMillis, MutableRow::new);
        }
        row.addBatch(batch, sequenceGap);
    }

    public synchronized void advanceClock() {
        if (latestObservedEventTimeMillis == Long.MIN_VALUE) return;
        long maturationTime = floorToRow(latestObservedEventTimeMillis) + ROW_DURATION_MILLIS;
        advanceTo(Math.min(Instant.now().toEpochMilli(), maturationTime));
    }

    public synchronized long latestEventTimeMillis() {
        return latestObservedEventTimeMillis;
    }

    synchronized void advanceTo(long nowMillis) {
        long currentRowStart = floorToRow(nowMillis);
        long safeThrough = currentRowStart - ALLOWED_LATENESS_MILLIS;
        if (finalizedThroughMillis == Long.MIN_VALUE) {
            if (completedRows.isEmpty()) return;
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
                MutableRow pending = pendingRows.computeIfAbsent(receiver.getKey(), ignored -> new TreeMap<>()).remove(next);
                MutableRow row = pending == null ? new MutableRow(next) : pending;
                row.trimSamples();
                receiver.getValue().addLast(row);
                while (receiver.getValue().size() > HISTORY_ROWS) receiver.getValue().removeFirst();
            }
            finalizedThroughMillis = next;
            next += ROW_DURATION_MILLIS;
        }
        pendingRows.values().forEach(rows -> rows.headMap(finalizedThroughMillis, true).clear());
    }

    public synchronized List<AoaRow> changedRowsSince(
            String receiverId,
            long fromMillis,
            Map<Long, Long> sentRevisions) {
        Deque<MutableRow> rows = completedRows.get(receiverId);
        if (rows == null || rows.isEmpty()) return List.of();
        List<AoaRow> changed = new ArrayList<>();
        for (MutableRow row : rows) {
            if (row.startTimeMillis >= fromMillis
                    && sentRevisions.getOrDefault(row.startTimeMillis, -1L) < row.revision) {
                changed.add(row.snapshot(receiverId));
                sentRevisions.put(row.startTimeMillis, row.revision);
            }
        }
        return changed;
    }

    public synchronized List<String> receiverIds() {
        return List.copyOf(completedRows.keySet());
    }

    private MutableRow findCompletedRow(String receiverId, long rowStartMillis) {
        Deque<MutableRow> rows = completedRows.get(receiverId);
        if (rows == null) return null;
        for (MutableRow row : rows) {
            if (row.startTimeMillis == rowStartMillis) return row;
        }
        return null;
    }

    private long floorToRow(long timeMillis) {
        return Math.floorDiv(timeMillis, ROW_DURATION_MILLIS) * ROW_DURATION_MILLIS;
    }

    private static final class MutableRow {
        private final long startTimeMillis;
        private long[] packedSamples = new long[64];
        private int sampleCount;
        private int inputPulseCount;
        private boolean gap;
        private long revision;

        private MutableRow(long startTimeMillis) {
            this.startTimeMillis = startTimeMillis;
        }

        private void addBatch(PdwPacketBatch batch, boolean sequenceGap) {
            gap |= sequenceGap;
            inputPulseCount = saturatingAdd(inputPulseCount, batch.pdws().size());
            for (Pdw pdw : batch.pdws()) {
                if (pdw.rfKHz() >= MIN_FREQUENCY_KHZ && pdw.rfKHz() < MAX_FREQUENCY_KHZ) {
                    append(pack(pdw));
                }
            }
            revision++;
        }

        private void append(long sample) {
            if (sampleCount == packedSamples.length) {
                packedSamples = java.util.Arrays.copyOf(packedSamples, Math.max(64, packedSamples.length * 2));
            }
            packedSamples[sampleCount++] = sample;
        }

        private void trimSamples() {
            if (packedSamples.length != sampleCount) {
                packedSamples = java.util.Arrays.copyOf(packedSamples, sampleCount);
            }
        }

        private AoaRow snapshot(String receiverId) {
            return new AoaRow(receiverId, startTimeMillis, ROW_DURATION_MILLIS, inputPulseCount, gap,
                    revision, java.util.Arrays.copyOf(packedSamples, sampleCount));
        }

        private static long pack(Pdw pdw) {
            return ((pdw.rfKHz() & 0xffff_ffffL) << 32)
                    | ((pdw.powerTenthDbm() & 0xffffL) << 16)
                    | (pdw.aoaHundredthDegree() & 0xffffL);
        }

        private static int saturatingAdd(int left, int right) {
            return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
        }
    }
}
