package com.github.mephisto_nox.radar_pulse_vis.aoa;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.SplittableRandom;
import org.springframework.stereotype.Component;

@Component
public class AoaTileEncoder {
    public static final int ANGLE_BIN_COUNT = 360;
    public static final int AMPLITUDE_BIN_WIDTH_TENTH_DBM = 10;

    private static final byte[] MAGIC = {'P', 'V', 'A', 'O'};
    private static final int HEADER_BYTES = 52;
    private static final int VERSION = 1;
    private static final int DEFAULT_RADIAL_BIN_COUNT = 50;

    public byte[] encode(AoaRow row, AoaView view, long nowMillis) {
        byte[] receiver = row.receiverId().getBytes(StandardCharsets.UTF_8);
        if (receiver.length > 0xffff) throw new IllegalArgumentException("Receiver ID is too long for an AOA tile");

        int radialBinCount = switch (view.radiusMode()) {
            case AMPLITUDE -> Math.max(1, (view.maxAmplitudeTenthDbm() - view.minAmplitudeTenthDbm())
                    / AMPLITUDE_BIN_WIDTH_TENTH_DBM);
            case FREQUENCY -> DEFAULT_RADIAL_BIN_COUNT;
            case AGE -> 1;
        };
        int radialMin = switch (view.radiusMode()) {
            case AMPLITUDE -> view.minAmplitudeTenthDbm();
            case FREQUENCY -> Math.toIntExact(view.minFrequencyKHz());
            case AGE -> 0;
        };
        int radialMax = switch (view.radiusMode()) {
            case AMPLITUDE -> view.maxAmplitudeTenthDbm();
            case FREQUENCY -> Math.toIntExact(view.maxFrequencyKHz());
            case AGE -> view.timeWindowMillis();
        };
        short[] counts = new short[ANGLE_BIN_COUNT * radialBinCount];
        ArrayList<Long> sampled = new ArrayList<>(view.sampleLimit());
        SplittableRandom random = new SplittableRandom(row.startTimeMillis() ^ row.receiverId().hashCode());
        int matchingCount = 0;
        int ageMillis = (int) Math.min(view.timeWindowMillis(), Math.max(0, nowMillis - row.startTimeMillis()));

        for (long packed : row.packedSamples()) {
            int angleHundredthDegree = (int) (packed & 0xffffL);
            int amplitudeTenthDbm = (short) ((packed >>> 16) & 0xffffL);
            long frequencyKHz = (packed >>> 32) & 0xffff_ffffL;
            if (frequencyKHz < view.minFrequencyKHz() || frequencyKHz >= view.maxFrequencyKHz()
                    || amplitudeTenthDbm < view.amplitudeThresholdTenthDbm()) {
                continue;
            }

            int radialBin = radialBin(view, amplitudeTenthDbm, frequencyKHz, ageMillis, radialBinCount);
            if (radialBin < 0) continue;
            int angleBin = angleHundredthDegree / 100;
            int index = radialBin * ANGLE_BIN_COUNT + angleBin;
            int current = Short.toUnsignedInt(counts[index]);
            if (current < 0xffff) counts[index] = (short) (current + 1);
            matchingCount++;
            reservoirSample(sampled, packed, matchingCount, view.sampleLimit(), random);
        }

        ByteBuffer tile = ByteBuffer.allocate(HEADER_BYTES + receiver.length
                + counts.length * Short.BYTES + sampled.size() * Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        tile.put(MAGIC);
        tile.put((byte) VERSION);
        tile.put((byte) (row.gap() ? 1 : 0));
        tile.putShort((short) receiver.length);
        tile.putLong(row.startTimeMillis());
        tile.putInt(row.durationMillis());
        tile.putInt(row.inputPulseCount());
        tile.putInt(matchingCount);
        tile.putShort((short) sampled.size());
        tile.putShort((short) ANGLE_BIN_COUNT);
        tile.putShort((short) radialBinCount);
        tile.put((byte) view.radiusMode().ordinal());
        tile.put((byte) 0);
        tile.putInt(radialMin);
        tile.putInt(radialMax);
        tile.putInt((int) view.minFrequencyKHz());
        tile.putInt((int) view.maxFrequencyKHz());
        tile.put(receiver);
        for (short count : counts) tile.putShort(count);
        for (long packed : sampled) tile.putLong(packed);
        return tile.array();
    }

    private int radialBin(
            AoaView view,
            int amplitudeTenthDbm,
            long frequencyKHz,
            int ageMillis,
            int radialBinCount) {
        return switch (view.radiusMode()) {
            case AMPLITUDE -> {
                if (amplitudeTenthDbm < view.minAmplitudeTenthDbm()
                        || amplitudeTenthDbm >= view.maxAmplitudeTenthDbm()) yield -1;
                yield (amplitudeTenthDbm - view.minAmplitudeTenthDbm())
                        / AMPLITUDE_BIN_WIDTH_TENTH_DBM;
            }
            case FREQUENCY -> (int) ((frequencyKHz - view.minFrequencyKHz()) * radialBinCount
                    / (view.maxFrequencyKHz() - view.minFrequencyKHz()));
            case AGE -> 0;
        };
    }

    private void reservoirSample(
            ArrayList<Long> samples,
            long packed,
            int seen,
            int limit,
            SplittableRandom random) {
        if (limit == 0) return;
        if (samples.size() < limit) {
            samples.add(packed);
            return;
        }
        long replacement = random.nextLong(seen);
        if (replacement < limit) samples.set((int) replacement, packed);
    }
}
