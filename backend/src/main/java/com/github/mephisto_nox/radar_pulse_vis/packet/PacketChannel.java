package com.github.mephisto_nox.radar_pulse_vis.packet;

import java.util.function.Consumer;

/** Broker-neutral publish/subscribe port for received packet metadata. */
public interface PacketChannel {
    void publish(ReceivedPacket packet);

    PacketSubscription subscribe(Consumer<ReceivedPacket> subscriber);
}
