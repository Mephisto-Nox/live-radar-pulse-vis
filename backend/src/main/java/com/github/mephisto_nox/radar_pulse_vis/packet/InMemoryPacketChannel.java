package com.github.mephisto_nox.radar_pulse_vis.packet;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-process fan-out implementation; replace with a broker adapter when needed. */
@Component
public class InMemoryPacketChannel implements PacketChannel {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryPacketChannel.class);

    private final CopyOnWriteArrayList<Consumer<ReceivedPacket>> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(ReceivedPacket packet) {
        for (Consumer<ReceivedPacket> subscriber : subscribers) {
            try {
                subscriber.accept(packet);
            } catch (RuntimeException exception) {
                logger.warn("Removing packet subscriber after delivery failure", exception);
                subscribers.remove(subscriber);
            }
        }
    }

    @Override
    public PacketSubscription subscribe(Consumer<ReceivedPacket> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }
}
