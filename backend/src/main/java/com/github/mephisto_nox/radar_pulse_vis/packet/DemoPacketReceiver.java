package com.github.mephisto_nox.radar_pulse_vis.packet;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class DemoPacketReceiver implements PacketReceiver {
    private static final long EMIT_INTERVAL_MILLIS = 250;
    private static final int MIN_PAYLOAD_LENGTH_BYTES = 24;
    private static final int MAX_PAYLOAD_LENGTH_BYTES = 32;

    private final AtomicLong sequence = new AtomicLong();
    private ScheduledExecutorService executor;
    private Consumer<ReceivedPacket> packetConsumer;

    @Override
    public synchronized void start(Consumer<ReceivedPacket> packetConsumer) {
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        this.packetConsumer = packetConsumer;
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "demo-packet-receiver");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::emitPacket, 0, EMIT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        packetConsumer = null;
    }

    private void emitPacket() {
        Consumer<ReceivedPacket> consumer = packetConsumer;
        if (consumer == null) {
            return;
        }
        int payloadLength = ThreadLocalRandom.current().nextInt(
                MIN_PAYLOAD_LENGTH_BYTES, MAX_PAYLOAD_LENGTH_BYTES + 1);
        consumer.accept(new ReceivedPacket("DEMO-01", sequence.getAndIncrement(), Instant.now(), payloadLength));
    }
}
