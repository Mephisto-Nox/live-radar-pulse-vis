package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class WaterfallTileEncoder {
    private static final byte[] MAGIC = {'P', 'V', 'W', 'F'};
    private static final int HEADER_BYTES = 44;
    private static final int VERSION = 1;

    public byte[] encode(String receiverId, WaterfallRow row) {
        return encode(receiverId, row, WaterfallAggregator.MIN_FREQUENCY_KHZ,
                WaterfallAggregator.MAX_FREQUENCY_KHZ, false, false, 0, 0);
    }

    public byte[] encode(
            String receiverId,
            WaterfallRow row,
            long requestedMinFrequencyKHz,
            long requestedMaxFrequencyKHz,
            boolean zoomed) {
        return encode(receiverId, row, requestedMinFrequencyKHz, requestedMaxFrequencyKHz,
                zoomed, false, 0, 0);
    }

    public byte[] encode(
            String receiverId,
            WaterfallRow row,
            long requestedMinFrequencyKHz,
            long requestedMaxFrequencyKHz,
            boolean zoomed,
            boolean sectorEnabled,
            int sectorStartHundredthDegree,
            int sectorEndHundredthDegree) {
        byte[] receiver = receiverId.getBytes(StandardCharsets.UTF_8);
        if (receiver.length > 0xffff) {
            throw new IllegalArgumentException("Receiver ID is too long for the waterfall tile protocol");
        }
        short[] coarseCounts = row.countsByAmplitudeAndFrequency();
        int expectedCounts = WaterfallAggregator.FREQUENCY_BIN_COUNT * WaterfallAggregator.AMPLITUDE_BIN_COUNT;
        if (coarseCounts.length != expectedCounts) {
            throw new IllegalArgumentException("Waterfall row has an unexpected histogram size");
        }
        long minFrequencyKHz = Math.max(WaterfallAggregator.MIN_FREQUENCY_KHZ, requestedMinFrequencyKHz);
        long maxFrequencyKHz = Math.min(WaterfallAggregator.MAX_FREQUENCY_KHZ, requestedMaxFrequencyKHz);
        boolean useZoom = zoomed && maxFrequencyKHz > minFrequencyKHz;
        int frequencyBinCount = WaterfallAggregator.FREQUENCY_BIN_COUNT;
        long tileMinFrequencyKHz = WaterfallAggregator.MIN_FREQUENCY_KHZ;
        long tileMaxFrequencyKHz = WaterfallAggregator.MAX_FREQUENCY_KHZ;
        short[] counts = coarseCounts;
        if (useZoom) {
            long zoomBinWidthKHz = (WaterfallAggregator.MAX_FREQUENCY_KHZ - WaterfallAggregator.MIN_FREQUENCY_KHZ)
                    / WaterfallAggregator.ZOOM_FREQUENCY_BIN_COUNT;
            int firstZoomBin = (int) ((minFrequencyKHz - WaterfallAggregator.MIN_FREQUENCY_KHZ) / zoomBinWidthKHz);
            int endZoomBin = (int) Math.min(WaterfallAggregator.ZOOM_FREQUENCY_BIN_COUNT,
                    (maxFrequencyKHz - WaterfallAggregator.MIN_FREQUENCY_KHZ + zoomBinWidthKHz - 1)
                            / zoomBinWidthKHz);
            firstZoomBin = Math.max(0, Math.min(firstZoomBin, WaterfallAggregator.ZOOM_FREQUENCY_BIN_COUNT - 1));
            endZoomBin = Math.max(firstZoomBin + 1, endZoomBin);
            frequencyBinCount = endZoomBin - firstZoomBin;
            tileMinFrequencyKHz = WaterfallAggregator.MIN_FREQUENCY_KHZ + firstZoomBin * zoomBinWidthKHz;
            tileMaxFrequencyKHz = WaterfallAggregator.MIN_FREQUENCY_KHZ + endZoomBin * zoomBinWidthKHz;
            counts = zoomCounts(row, firstZoomBin, endZoomBin, sectorEnabled,
                    sectorStartHundredthDegree, sectorEndHundredthDegree);
        } else if (sectorEnabled) {
            counts = sectorCounts(row, sectorStartHundredthDegree, sectorEndHundredthDegree);
        }

        ByteBuffer tile = ByteBuffer.allocate(HEADER_BYTES + receiver.length + counts.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        tile.put(MAGIC);
        tile.put((byte) VERSION);
        tile.put((byte) (row.gap() ? 1 : 0));
        tile.putShort((short) receiver.length);
        tile.putInt((int) (row.startTimeMillis() / WaterfallAggregator.ROW_DURATION_MILLIS));
        tile.putLong(row.startTimeMillis());
        tile.putInt(WaterfallAggregator.ROW_DURATION_MILLIS);
        tile.putInt(row.inputPulseCount());
        tile.putShort((short) frequencyBinCount);
        tile.putShort((short) WaterfallAggregator.AMPLITUDE_BIN_COUNT);
        tile.putInt((int) tileMinFrequencyKHz);
        tile.putInt((int) tileMaxFrequencyKHz);
        tile.putShort((short) WaterfallAggregator.MIN_POWER_TENTH_DBM);
        tile.putShort((short) WaterfallAggregator.MAX_POWER_TENTH_DBM);
        tile.put(receiver);
        for (short count : counts) {
            tile.putShort(count);
        }
        return tile.array();
    }

    private short[] zoomCounts(
            WaterfallRow row,
            int firstZoomBin,
            int endZoomBin,
            boolean sectorEnabled,
            int sectorStartHundredthDegree,
            int sectorEndHundredthDegree) {
        int zoomBinCount = endZoomBin - firstZoomBin;
        short[] counts = new short[zoomBinCount * WaterfallAggregator.AMPLITUDE_BIN_COUNT];
        for (long packedSample : row.zoomSamples()) {
            int zoomBin = (int) (packedSample >>> 22);
            if (zoomBin < firstZoomBin || zoomBin >= endZoomBin) continue;
            int angle = (int) (packedSample & 0xffffL);
            if (sectorEnabled && !isInSector(angle, sectorStartHundredthDegree, sectorEndHundredthDegree)) continue;
            int amplitudeBin = (int) ((packedSample >>> 16) & 0x3fL);
            int index = amplitudeBin * zoomBinCount + zoomBin - firstZoomBin;
            int current = Short.toUnsignedInt(counts[index]);
            if (current < 0xffff) counts[index] = (short) (current + 1);
        }
        return counts;
    }

    private short[] sectorCounts(WaterfallRow row, int sectorStartHundredthDegree, int sectorEndHundredthDegree) {
        short[] counts = new short[WaterfallAggregator.AMPLITUDE_BIN_COUNT * WaterfallAggregator.FREQUENCY_BIN_COUNT];
        for (long packedSample : row.zoomSamples()) {
            int angle = (int) (packedSample & 0xffffL);
            if (!isInSector(angle, sectorStartHundredthDegree, sectorEndHundredthDegree)) continue;
            int zoomBin = (int) (packedSample >>> 22);
            int frequencyBin = zoomBin * WaterfallAggregator.FREQUENCY_BIN_COUNT
                    / WaterfallAggregator.ZOOM_FREQUENCY_BIN_COUNT;
            int amplitudeBin = (int) ((packedSample >>> 16) & 0x3fL);
            int index = amplitudeBin * WaterfallAggregator.FREQUENCY_BIN_COUNT + frequencyBin;
            int current = Short.toUnsignedInt(counts[index]);
            if (current < 0xffff) counts[index] = (short) (current + 1);
        }
        return counts;
    }

    private boolean isInSector(int angle, int start, int end) {
        if (start <= end) return angle >= start && angle < end;
        return angle >= start || angle < end;
    }
}
