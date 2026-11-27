package com.github.mephisto_nox.radar_pulse_vis.packet;

import java.time.Instant;
import java.util.Objects;

public record ReceivedPacket(
        String receiverId,
        long sequenceNumber,
        Instant receivedAt,
        int payloadLengthBytes) {

    public ReceivedPacket {
        if (receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException("receiverId must not be blank");
        }
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must not be negative");
        }
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        if (payloadLengthBytes < 0) {
            throw new IllegalArgumentException("payloadLengthBytes must not be negative");
        }
    }
}
