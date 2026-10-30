package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** WM_Central crea al arrancar los topics que aún no existen. */
@Configuration
class KafkaTopics {

    /** Grupo de consumidores de WM_Central. */
    static final String CENTRAL_GROUP = "central";

    // Particiones de cada topic. WM_FO lee las de STATION_STATUS por número ("0-2"):
    // si cambia este valor, hay que cambiarlo también allí.
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
