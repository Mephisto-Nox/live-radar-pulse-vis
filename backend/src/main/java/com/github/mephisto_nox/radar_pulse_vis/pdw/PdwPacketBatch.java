package com.github.mephisto_nox.radar_pulse_vis.pdw;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PdwPacketBatch(
        String receiverId,
        long packetSequence,
        long firstToaUs,
        Instant receivedAt,
        List<Pdw> pdws) {

    public PdwPacketBatch {
        if (receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException("receiverId must not be blank");
        }
        if (packetSequence < 0 || packetSequence > 0xffff_ffffL) {
            throw new IllegalArgumentException("packetSequence must fit in an unsigned 32-bit integer");
        }
        if (firstToaUs < 0) {
            throw new IllegalArgumentException("firstToaUs must fit in a non-negative Java long");
        }
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        pdws = List.copyOf(Objects.requireNonNull(pdws, "pdws must not be null"));
        if (pdws.isEmpty()) {
            throw new IllegalArgumentException("A packet batch must contain at least one PDW");
        }
    }
}
