package com.github.mephisto_nox.radar_pulse_vis.pdw;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class PdwUdpReceiver implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(PdwUdpReceiver.class);
    private static final int MAX_UDP_DATAGRAM_BYTES = 65_507;
    private static final int DEFAULT_SOCKET_BUFFER_BYTES = 4 * 1024 * 1024;
    private static final long SHUTDOWN_WAIT_MILLIS = 2_000;

    private final PdwPacketDecoder decoder;
    private final PdwKafkaPublisher publisher;
    private final PdwIngestMetrics metrics;
    private final String bindAddress;
    private final int port;
    private final String receiverId;
    private final int socketBufferBytes;
    private final AtomicBoolean running = new AtomicBoolean();

    private DatagramSocket socket;
    private Thread receiverThread;

    public PdwUdpReceiver(
            PdwPacketDecoder decoder,
            PdwKafkaPublisher publisher,
            PdwIngestMetrics metrics,
            @Value("${pulsevision.udp.bind-address:0.0.0.0}") String bindAddress,
            @Value("${pulsevision.udp.port:5005}") int port,
            @Value("${pulsevision.udp.receiver-id:RX-01}") String receiverId,
            @Value("${pulsevision.udp.receive-buffer-bytes:4194304}") int socketBufferBytes) {
        this.decoder = decoder;
        this.publisher = publisher;
        this.metrics = metrics;
        this.bindAddress = bindAddress;
        this.port = port;
        this.receiverId = receiverId;
        this.socketBufferBytes = socketBufferBytes;
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setReceiveBufferSize(socketBufferBytes);
            InetAddress address = InetAddress.getByName(bindAddress);
            socket.bind(new InetSocketAddress(address, port));
        } catch (IOException exception) {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            throw new IllegalStateException("Could not bind PDW UDP receiver to " + bindAddress + ":" + port, exception);
        }

        running.set(true);
        receiverThread = new Thread(this::receiveDatagrams, "pdw-udp-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
        logger.info("PDW UDP receiver listening on {}:{} for receiver {}", bindAddress, port, receiverId);
    }

    @Override
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        socket.close();
        try {
            receiverThread.join(SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for the PDW UDP receiver to stop", exception);
        }
        socket = null;
        receiverThread = null;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return 100;
    }

    private void receiveDatagrams() {
        byte[] buffer = new byte[MAX_UDP_DATAGRAM_BYTES];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        while (running.get()) {
            try {
                datagram.setLength(buffer.length);
                socket.receive(datagram);
                metrics.datagramReceived();
                PdwPacketBatch batch = decoder.decode(buffer, datagram.getLength(), receiverId, Instant.now());
                metrics.pdwsDecoded(batch.pdws().size());
                publisher.publish(batch);
            } catch (InvalidPdwDatagramException exception) {
                long rejected = metrics.datagramRejected();
                if (rejected == 1 || rejected % 100 == 0) {
                    logger.warn("Rejected {} invalid PDW datagrams so far; latest reason: {}", rejected, exception.getMessage());
                }
            } catch (SocketException exception) {
                if (running.get()) {
                    logger.error("PDW UDP socket failed while receiving datagrams", exception);
                    running.set(false);
                }
            } catch (IOException exception) {
                if (running.get()) {
                    logger.warn("Could not receive a PDW UDP datagram", exception);
                }
            }
        }
    }
}
