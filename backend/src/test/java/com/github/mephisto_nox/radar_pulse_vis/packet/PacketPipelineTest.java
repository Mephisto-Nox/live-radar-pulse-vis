package com.github.mephisto_nox.radar_pulse_vis.packet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PacketPipelineTest {
    @Mock
    private PacketReceiver packetReceiver;

    @Mock
    private PacketChannel packetChannel;

    @Test
    void startsReceiverAndPublishesReceivedPacketsToChannel() {
        PacketPipeline pipeline = new PacketPipeline(packetReceiver, packetChannel);
        ReceivedPacket packet = new ReceivedPacket("RX-01", 1, Instant.now(), 28);
        ArgumentCaptor<Consumer<ReceivedPacket>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);

        pipeline.start();
        verify(packetReceiver).start(consumerCaptor.capture());
        consumerCaptor.getValue().accept(packet);

        verify(packetChannel).publish(packet);
        assertThat(pipeline.isRunning()).isTrue();
        pipeline.stop();
        verify(packetReceiver).stop();
        assertThat(pipeline.isRunning()).isFalse();
    }

    @Test
    void doesNotStartReceiverMoreThanOnce() {
        PacketPipeline pipeline = new PacketPipeline(packetReceiver, packetChannel);

        pipeline.start();
        pipeline.start();

        verify(packetReceiver).start(org.mockito.ArgumentMatchers.any());
        verify(packetReceiver, never()).stop();
        pipeline.stop();
    }
}
