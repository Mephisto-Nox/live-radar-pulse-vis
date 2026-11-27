package com.github.mephisto_nox.radar_pulse_vis.pdw;

public record Pdw(
        long toaNs,
        long rfKHz,
        long pwNs,
        int powerTenthDbm,
        int aoaHundredthDegree) {

    public Pdw {
        if (toaNs < 0 || rfKHz < 0 || pwNs < 0) {
            throw new IllegalArgumentException("Unsigned PDW fields must fit in a non-negative Java long");
        }
        if (aoaHundredthDegree < 0 || aoaHundredthDegree > 35_999) {
            throw new IllegalArgumentException("AOA must be in the range 0 to 359.99 degrees");
        }
    }
}
