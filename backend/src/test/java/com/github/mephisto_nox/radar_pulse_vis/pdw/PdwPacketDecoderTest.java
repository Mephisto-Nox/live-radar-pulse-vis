package com.github.mephisto_nox.radar_pulse_vis.pdw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class PdwPacketDecoderTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-15T10:00:00Z");
    private final PdwPacketDecoder decoder = new PdwPacketDecoder();

    @Test
    void decodesWaterfallFieldsAndIgnoresTrailingFiller() throws Exception {
        byte[] datagram = sampleDatagram(5);

        PdwPacketBatch batch = decoder.decode(datagram, datagram.length, "RX-01", RECEIVED_AT);

        assertThat(batch.receiverId()).isEqualTo("RX-01");
        assertThat(batch.packetSequence()).isEqualTo(0xffff_ffffL);
        assertThat(batch.firstToaUs()).isEqualTo(2_939_815L);
        assertThat(batch.receivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(batch.pdws()).containsExactly(new Pdw(2_939_815_340L, 15_507_862L, 402L, -359, 10_437));
    }

    @Test
    void rejectsDatagramWithInvalidSyncWord() {
        byte[] datagram = sampleDatagram(0);
        datagram[0] = 0;

        assertThatThrownBy(() -> decoder.decode(datagram, datagram.length, "RX-01", RECEIVED_AT))
                .isInstanceOf(InvalidPdwDatagramException.class)
                .hasMessageContaining("sync word");
    }

    @Test
    void rejectsTruncatedPdwPayload() {
        byte[] datagram = sampleDatagram(0);

        assertThatThrownBy(() -> decoder.decode(datagram, PdwPacketDecoder.HEADER_SIZE_BYTES + 10, "RX-01", RECEIVED_AT))
                .isInstanceOf(InvalidPdwDatagramException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    void rejectsInvalidPayloadCrc() {
        byte[] datagram = sampleDatagram(0);
        datagram[PdwPacketDecoder.HEADER_SIZE_BYTES] ^= 0x01;

        assertThatThrownBy(() -> decoder.decode(datagram, datagram.length, "RX-01", RECEIVED_AT))
                .isInstanceOf(InvalidPdwDatagramException.class)
                .hasMessageContaining("CRC-32");
    }

    @Test
    void rejectsAngleOutsideDocumentedCircle() {
        byte[] datagram = sampleDatagram(0);
        ByteBuffer.wrap(datagram)
                .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(PdwPacketDecoder.HEADER_SIZE_BYTES + 18, (short) 36_000);
        ByteBuffer.wrap(datagram)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(18, (int) crc32(datagram, PdwPacketDecoder.HEADER_SIZE_BYTES, PdwPacketDecoder.PDW_SIZE_BYTES));

        assertThatThrownBy(() -> decoder.decode(datagram, datagram.length, "RX-01", RECEIVED_AT))
                .isInstanceOf(InvalidPdwDatagramException.class)
                .hasMessageContaining("AOA");
    }

    private byte[] sampleDatagram(int fillerBytes) {
        int payloadLength = PdwPacketDecoder.PDW_SIZE_BYTES;
        byte[] datagram = new byte[PdwPacketDecoder.HEADER_SIZE_BYTES + payloadLength + fillerBytes];
        ByteBuffer payload = ByteBuffer.wrap(datagram, PdwPacketDecoder.HEADER_SIZE_BYTES, payloadLength)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        payload.putLong(2_939_815_340L);
        payload.putInt(15_507_862);
        payload.putInt(402);
        payload.putShort((short) -359);
        payload.putShort((short) 10_437);
        payload.putShort((short) 50);
        payload.putShort((short) 0);

        ByteBuffer header = ByteBuffer.wrap(datagram).order(ByteOrder.BIG_ENDIAN);
        header.putInt(PdwPacketDecoder.SYNC_WORD);
        header.putInt(-1);
        header.putShort((short) 1);
        header.putLong(2_939_815L);
        header.putInt((int) crc32(datagram, PdwPacketDecoder.HEADER_SIZE_BYTES, payloadLength));
        return datagram;
    }

    private long crc32(byte[] datagram, int offset, int length) {
        CRC32 crc32 = new CRC32();
        crc32.update(datagram, offset, length);
        return crc32.getValue();
    }
}
