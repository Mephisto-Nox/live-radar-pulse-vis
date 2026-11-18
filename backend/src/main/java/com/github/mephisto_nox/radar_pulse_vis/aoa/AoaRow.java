package com.github.mephisto_nox.radar_pulse_vis.aoa;

public record AoaRow(
        String receiverId,
        long startTimeMillis,
        int durationMillis,
        int inputPulseCount,
        boolean gap,
        long revision,
        long[] packedSamples) {
    public AoaRow {
        if (receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException("receiverId must not be blank");
        }
        if (durationMillis <= 0 || inputPulseCount < 0) {
            throw new IllegalArgumentException("Duration must be positive and pulse count non-negative");
        }
        if (packedSamples == null) {
            throw new IllegalArgumentException("packedSamples must not be null");
        }
    }
}
