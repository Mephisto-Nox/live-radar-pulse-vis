package com.github.mephisto_nox.radar_pulse_vis.pdw;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import org.springframework.stereotype.Component;

@Component
public class PdwPacketDecoder {
    static final int HEADER_SIZE_BYTES = 22;
    static final int PDW_SIZE_BYTES = 24;
    static final int SYNC_WORD = 0xA5A55A5A;

    public PdwPacketBatch decode(byte[] datagram, int length, String receiverId, Instant receivedAt)
            throws InvalidPdwDatagramException {
        if (datagram == null || length < HEADER_SIZE_BYTES || length > datagram.length) {
            throw new InvalidPdwDatagramException("Datagram is shorter than the packet header or has an invalid length");
        }

        ByteBuffer header = ByteBuffer.wrap(datagram, 0, length).order(ByteOrder.BIG_ENDIAN);
        if (header.getInt() != SYNC_WORD) {
            throw new InvalidPdwDatagramException("Packet sync word does not match");
        }

        long sequence = Integer.toUnsignedLong(header.getInt());
        int pdwCount = Short.toUnsignedInt(header.getShort());
        long firstToaUs = header.getLong();
        long expectedCrc = Integer.toUnsignedLong(header.getInt());
        if (pdwCount == 0) {
            throw new InvalidPdwDatagramException("Packet contains no PDWs");
        }
        if (firstToaUs < 0) {
            throw new InvalidPdwDatagramException("First TOA exceeds the supported signed 64-bit range");
        }

        int payloadLength = pdwCount * PDW_SIZE_BYTES;
        int payloadOffset = HEADER_SIZE_BYTES;
        if (length < payloadOffset + payloadLength) {
            throw new InvalidPdwDatagramException("Datagram is truncated before all declared PDWs");
        }
        verifyCrc(datagram, payloadOffset, payloadLength, expectedCrc);

        ByteBuffer payload = ByteBuffer.wrap(datagram, payloadOffset, payloadLength)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        List<Pdw> pdws = new ArrayList<>(pdwCount);
        for (int index = 0; index < pdwCount; index++) {
            pdws.add(readPdw(payload));
        }
        return new PdwPacketBatch(receiverId, sequence, firstToaUs, receivedAt, pdws);
    }

    private void verifyCrc(byte[] datagram, int payloadOffset, int payloadLength, long expectedCrc)
            throws InvalidPdwDatagramException {
        CRC32 crc32 = new CRC32();
        crc32.update(datagram, payloadOffset, payloadLength);
        if (crc32.getValue() != expectedCrc) {
            throw new InvalidPdwDatagramException("PDW payload CRC-32 does not match");
        }
    }

    private Pdw readPdw(ByteBuffer payload) throws InvalidPdwDatagramException {
        long toaNs = payload.getLong();
        long rfKHz = Integer.toUnsignedLong(payload.getInt());
        long pwNs = Integer.toUnsignedLong(payload.getInt());
        int powerTenthDbm = payload.getShort();
        int aoaHundredthDegree = Short.toUnsignedInt(payload.getShort());
        payload.getShort();
        payload.getShort();

        try {
            return new Pdw(toaNs, rfKHz, pwNs, powerTenthDbm, aoaHundredthDegree);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPdwDatagramException("PDW contains a value outside the supported range: " + exception.getMessage());
        }
    }
}
