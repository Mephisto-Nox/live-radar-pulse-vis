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
        byte[] receiver = receiverId.getBytes(StandardCharsets.UTF_8);
        if (receiver.length > 0xffff) {
            throw new IllegalArgumentException("Receiver ID is too long for the waterfall tile protocol");
        }
        short[] counts = row.countsByAmplitudeAndFrequency();
        int expectedCounts = WaterfallAggregator.FREQUENCY_BIN_COUNT * WaterfallAggregator.AMPLITUDE_BIN_COUNT;
        if (counts.length != expectedCounts) {
            throw new IllegalArgumentException("Waterfall row has an unexpected histogram size");
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
        tile.putShort((short) WaterfallAggregator.FREQUENCY_BIN_COUNT);
        tile.putShort((short) WaterfallAggregator.AMPLITUDE_BIN_COUNT);
        tile.putInt((int) WaterfallAggregator.MIN_FREQUENCY_KHZ);
        tile.putInt((int) WaterfallAggregator.MAX_FREQUENCY_KHZ);
        tile.putShort((short) WaterfallAggregator.MIN_POWER_TENTH_DBM);
        tile.putShort((short) WaterfallAggregator.MAX_POWER_TENTH_DBM);
        tile.put(receiver);
        for (short count : counts) {
            tile.putShort(count);
        }
        return tile.array();
    }
}
