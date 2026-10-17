package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** WM_Central creates every topic on start-up if it does not exist yet. */
@Configuration
class KafkaTopics {

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic irrigationRequestsTopic() {
        return TopicBuilder.name(Topics.IRRIGATION_REQUESTS).partitions(PARTITIONS).build();
    }

    @Bean
    NewTopic stationCommandsTopic() {
        return TopicBuilder.name(Topics.STATION_COMMANDS).partitions(PARTITIONS).build();
    }

    @Bean
    NewTopic stationTelemetryTopic() {
        return TopicBuilder.name(Topics.STATION_TELEMETRY).partitions(PARTITIONS).build();
    }

    @Bean
    NewTopic operatorNotificationsTopic() {
        return TopicBuilder.name(Topics.OPERATOR_NOTIFICATIONS).partitions(PARTITIONS).build();
    }

    @Bean
    NewTopic stationStatusTopic() {
        return TopicBuilder.name(Topics.STATION_STATUS).partitions(PARTITIONS).build();
    }
}
