package com.github.mephisto_nox.radar_pulse_vis.pdw;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PdwKafkaPublisher implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(PdwKafkaPublisher.class);
    private static final long SHUTDOWN_WAIT_MILLIS = 10_000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PdwIngestMetrics metrics;
    private final String topic;
    private final BlockingQueue<PdwPacketBatch> queue;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    public PdwKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PdwIngestMetrics metrics,
            @Value("${pulsevision.kafka.topic}") String topic,
            @Value("${pulsevision.kafka.queue-capacity:4096}") int queueCapacity) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.topic = topic;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    public void publish(PdwPacketBatch batch) {
        if (!queue.offer(batch)) {
            long dropped = metrics.batchDropped();
            if (dropped == 1 || dropped % 100 == 0) {
                logger.warn("Kafka publish queue is full; dropped {} PDW batches so far", dropped);
            }
        }
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::publishQueuedBatches, "pdw-kafka-publisher");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(SHUTDOWN_WAIT_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for the PDW Kafka publisher to stop", exception);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private void publishQueuedBatches() {
        while (running.get() || !queue.isEmpty()) {
            try {
                PdwPacketBatch batch = queue.poll(250, TimeUnit.MILLISECONDS);
                if (batch != null) {
                    send(batch);
                }
            } catch (InterruptedException exception) {
                if (running.get()) {
                    running.set(false);
                    Thread.currentThread().interrupt();
                    logger.warn("PDW Kafka publisher worker was interrupted unexpectedly", exception);
                    return;
                }
            }
        }
    }

    private void send(PdwPacketBatch batch) {
        try {
            String payload = objectMapper.writeValueAsString(batch);
            kafkaTemplate.send(topic, batch.receiverId(), payload)
                    .whenComplete((result, exception) -> {
                        if (exception == null) {
                            metrics.batchPublished();
                        } else {
                            metrics.publishFailed();
                            logger.warn("Kafka rejected PDW batch for receiver {} sequence {}",
                                    batch.receiverId(), batch.packetSequence(), exception);
                        }
                    });
        } catch (RuntimeException exception) {
            metrics.publishFailed();
            logger.warn("Could not submit PDW batch for receiver {} sequence {} to Kafka",
                    batch.receiverId(), batch.packetSequence(), exception);
        }
    }
}
