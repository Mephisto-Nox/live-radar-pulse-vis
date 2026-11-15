package com.github.mephisto_nox.radar_pulse_vis.packet;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class PacketPipeline implements SmartLifecycle {
    private final PacketReceiver packetReceiver;
    private final PacketChannel packetChannel;
    private final AtomicBoolean running = new AtomicBoolean();

    public PacketPipeline(PacketReceiver packetReceiver, PacketChannel packetChannel) {
        this.packetReceiver = packetReceiver;
        this.packetChannel = packetChannel;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                packetReceiver.start(packetChannel::publish);
            } catch (RuntimeException exception) {
                running.set(false);
                throw exception;
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            packetReceiver.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
