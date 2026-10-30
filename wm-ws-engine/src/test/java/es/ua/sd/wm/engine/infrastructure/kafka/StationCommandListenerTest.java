package es.ua.sd.wm.engine.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.Topics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import tools.jackson.databind.json.JsonMapper;

/** Un Kafka real (embebido): el Engine solo debe quedarse con las órdenes de su estación. */
@EmbeddedKafka(topics = Topics.STATION_COMMANDS, partitions = 3)
class StationCommandListenerTest {

    private final List<StationCommand> executed = new CopyOnWriteArrayList<>();
    private StationCommandListener listener;

    @AfterEach
    void stopListening() {
        listener.stop();
    }

    @Test
    void executesOnlyTheOrdersForItsOwnStation(EmbeddedKafkaBroker broker) {
        listener = new StationCommandListener(consumerFactory(broker), executed::add, JsonMapper.builder().build());
        listener.listenFor("WS-01");
        KafkaTemplate<String, String> kafka = kafkaTemplate(broker);

        await().atMost(Duration.ofSeconds(15)).until(() -> {
            // Las órdenes enviadas antes de que el listener esté listo se pierden: se envían hasta que llega una.
            kafka.send(Topics.STATION_COMMANDS, "WS-02", startJson("R2", "WS-02"));
            kafka.send(Topics.STATION_COMMANDS, "WS-01", startJson("R1", "WS-01"));
            return !executed.isEmpty();
        });

        assertThat(executed).allMatch(command -> command.stationId().equals("WS-01"));
    }

    private static String startJson(String requestId, String stationId) {
        return """
                {"requestId":"%s","type":"START","stationId":"%s","operatorId":"FO-001","limitSeconds":60}
                """.formatted(requestId, stationId);
    }

    private static DefaultKafkaConsumerFactory<String, String> consumerFactory(EmbeddedKafkaBroker broker) {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"),
                new StringDeserializer(), new StringDeserializer());
    }

    private static KafkaTemplate<String, String> kafkaTemplate(EmbeddedKafkaBroker broker) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()),
                new StringSerializer(), new StringSerializer()));
    }
}
