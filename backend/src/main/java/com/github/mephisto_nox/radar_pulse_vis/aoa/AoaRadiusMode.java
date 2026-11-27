package com.github.mephisto_nox.radar_pulse_vis.aoa;

public enum AoaRadiusMode {
    AMPLITUDE,
    FREQUENCY,
    AGE;

    public static AoaRadiusMode fromCommand(String value) {
        if (value == null) return AMPLITUDE;
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "frequency" -> FREQUENCY;
            case "age" -> AGE;
            default -> AMPLITUDE;
        };
    }
}
