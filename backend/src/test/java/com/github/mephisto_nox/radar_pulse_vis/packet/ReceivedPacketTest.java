package com.github.mephisto_nox.radar_pulse_vis.packet;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReceivedPacketTest {
    @Test
    void rejectsBlankReceiverId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReceivedPacket(" ", 1, Instant.now(), 24));
    }

    @Test
    void rejectsNegativeSequenceAndPayloadLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReceivedPacket("RX-01", -1, Instant.now(), 24));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReceivedPacket("RX-01", 1, Instant.now(), -1));
    }
}
