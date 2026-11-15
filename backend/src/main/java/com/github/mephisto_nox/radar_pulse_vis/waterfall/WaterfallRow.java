package com.github.mephisto_nox.radar_pulse_vis.waterfall;

public record WaterfallRow(
    long startTimeMillis,
    int inputPulseCount,
    boolean gap,
    long revision,
    short[] countsByAmplitudeAndFrequency) {
    public WaterfallRow {
        if (inputPulseCount < 0) {
            throw new IllegalArgumentException("inputPulseCount must not be negative");
        }
        if (countsByAmplitudeAndFrequency == null) {
            throw new IllegalArgumentException("countsByAmplitudeAndFrequency must not be null");
        }
    }
}
