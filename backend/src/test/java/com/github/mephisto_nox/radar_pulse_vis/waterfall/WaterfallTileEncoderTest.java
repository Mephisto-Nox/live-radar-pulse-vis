package com.github.mephisto_nox.radar_pulse_vis.waterfall;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WaterfallTileEncoderTest {
    private final WaterfallTileEncoder encoder = new WaterfallTileEncoder();

    @Test
    void encodesVersionedBinaryTileWithReceiverAndHistogram() {
        short[] counts = new short[WaterfallAggregator.FREQUENCY_BIN_COUNT * WaterfallAggregator.AMPLITUDE_BIN_COUNT];
        counts[15 * WaterfallAggregator.FREQUENCY_BIN_COUNT + 42] = 7;
        byte[] encoded = encoder.encode("RX-01", new WaterfallRow(1_000, 10, false, 1, counts));

        ByteBuffer tile = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(new String(encoded, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("PVWF");
        assertThat(Byte.toUnsignedInt(tile.get(4))).isEqualTo(1);
        assertThat(Short.toUnsignedInt(tile.getShort(6))).isEqualTo(5);
        assertThat(new String(encoded, 44, 5, StandardCharsets.UTF_8)).isEqualTo("RX-01");
        int countsOffset = 44 + 5;
        assertThat(Short.toUnsignedInt(tile.getShort(countsOffset + (15 * WaterfallAggregator.FREQUENCY_BIN_COUNT + 42) * Short.BYTES)))
                .isEqualTo(7);
        assertThat(encoded).hasSize(44 + 5 + counts.length * Short.BYTES);
    }

    @Test
    void marksRowsWithPacketLossAsGaps() {
        short[] counts = new short[WaterfallAggregator.FREQUENCY_BIN_COUNT * WaterfallAggregator.AMPLITUDE_BIN_COUNT];

        byte[] encoded = encoder.encode("RX-01", new WaterfallRow(1_000, 10, true, 1, counts));

        assertThat(Byte.toUnsignedInt(encoded[5])).isEqualTo(1);
    }
}
