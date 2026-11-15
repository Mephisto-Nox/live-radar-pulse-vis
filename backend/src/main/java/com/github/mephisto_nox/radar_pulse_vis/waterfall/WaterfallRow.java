package com.github.mephisto_nox.radar_pulse_vis.waterfall;

public record WaterfallRow(
    long startTimeMillis,
    int inputPulseCount,
    boolean gap,
    long revision,
    short[] countsByAmplitudeAndFrequency,
    int[] zoomSamples) {
    public WaterfallRow {
        if (inputPulseCount < 0) {
            throw new IllegalArgumentException("inputPulseCount must not be negative");
        }
        if (countsByAmplitudeAndFrequency == null) {
            throw new IllegalArgumentException("countsByAmplitudeAndFrequency must not be null");
        }
        if (zoomSamples == null) {
            throw new IllegalArgumentException("zoomSamples must not be null");
        }
    }

    public WaterfallRow(
            long startTimeMillis,
            int inputPulseCount,
            boolean gap,
            long revision,
            short[] countsByAmplitudeAndFrequency) {
        this(startTimeMillis, inputPulseCount, gap, revision, countsByAmplitudeAndFrequency, new int[0]);
    }
}
