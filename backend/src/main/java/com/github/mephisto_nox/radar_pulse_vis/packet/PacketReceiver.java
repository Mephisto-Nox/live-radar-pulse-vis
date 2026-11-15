package com.github.mephisto_nox.radar_pulse_vis.packet;

import java.util.function.Consumer;

/** Replaceable boundary for a receiver-specific transport implementation. */
public interface PacketReceiver {
    void start(Consumer<ReceivedPacket> packetConsumer);

    void stop();
}
