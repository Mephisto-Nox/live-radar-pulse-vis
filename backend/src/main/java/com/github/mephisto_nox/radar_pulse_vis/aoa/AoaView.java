package com.github.mephisto_nox.radar_pulse_vis.aoa;

public record AoaView(
        long minFrequencyKHz,
        long maxFrequencyKHz,
        int amplitudeThresholdTenthDbm,
        int minAmplitudeTenthDbm,
        int maxAmplitudeTenthDbm,
        int timeWindowMillis,
        int sampleLimit,
        AoaRadiusMode radiusMode) {
    public AoaView {
        if (minFrequencyKHz < AoaAggregator.MIN_FREQUENCY_KHZ
                || maxFrequencyKHz > AoaAggregator.MAX_FREQUENCY_KHZ
                || maxFrequencyKHz <= minFrequencyKHz) {
            throw new IllegalArgumentException("AOA frequency range must be within 0 to 60 GHz");
        }
        if (maxAmplitudeTenthDbm <= minAmplitudeTenthDbm || timeWindowMillis <= 0 || sampleLimit < 0) {
            throw new IllegalArgumentException("Invalid AOA display range or sampling limit");
        }
        if (radiusMode == null) throw new IllegalArgumentException("radiusMode must not be null");
    }
}
