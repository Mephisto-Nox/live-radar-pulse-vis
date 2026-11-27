package com.github.mephisto_nox.radar_pulse_vis.pdw;

import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {
    private static final long RETENTION_MILLIS = 300_000;
    private static final long SEGMENT_MILLIS = 60_000;

    @Bean
    NewTopic pdwWaterfallTopic(
            @Value("${pulsevision.kafka.topic}") String topicName,
            @Value("${pulsevision.kafka.partitions:16}") int partitions,
            @Value("${pulsevision.kafka.replication-factor:1}") short replicationFactor) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .configs(Map.of(
                        TopicConfig.RETENTION_MS_CONFIG, Long.toString(RETENTION_MILLIS),
                        TopicConfig.SEGMENT_MS_CONFIG, Long.toString(SEGMENT_MILLIS)))
                .build();
    }
}
