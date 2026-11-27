package com.github.mephisto_nox.radar_pulse_vis.packet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryPacketChannelTest {
    private final InMemoryPacketChannel channel = new InMemoryPacketChannel();

    @Test
    void publishesPacketsToEveryActiveSubscriber() {
        List<ReceivedPacket> firstSubscriberPackets = new ArrayList<>();
        List<ReceivedPacket> secondSubscriberPackets = new ArrayList<>();
        channel.subscribe(firstSubscriberPackets::add);
        channel.subscribe(secondSubscriberPackets::add);
        ReceivedPacket packet = samplePacket();

        channel.publish(packet);

        assertThat(firstSubscriberPackets).containsExactly(packet);
        assertThat(secondSubscriberPackets).containsExactly(packet);
    }

    @Test
    void stopsDeliveringPacketsAfterSubscriberUnsubscribes() {
        List<ReceivedPacket> packets = new ArrayList<>();
        PacketSubscription subscription = channel.subscribe(packets::add);
        subscription.close();

        channel.publish(samplePacket());

        assertThat(packets).isEmpty();
    }

    @Test
    void removesFailingSubscriberWithoutBlockingOtherSubscribers() {
        List<ReceivedPacket> packets = new ArrayList<>();
        channel.subscribe(packet -> {
            throw new IllegalStateException("subscriber failed");
        });
        channel.subscribe(packets::add);
        ReceivedPacket packet = samplePacket();

        channel.publish(packet);

        assertThat(packets).containsExactly(packet);
    }

    private ReceivedPacket samplePacket() {
        return new ReceivedPacket("RX-01", 1, Instant.parse("2026-11-15T10:00:00Z"), 28);
    }
}
