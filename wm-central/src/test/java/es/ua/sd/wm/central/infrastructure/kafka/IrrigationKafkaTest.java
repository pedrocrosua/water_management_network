package es.ua.sd.wm.central.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.common.model.StationState;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The real WM_Central with a real (embedded) Kafka. The test plays a WM_FO and a WM_WS_E. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class IrrigationKafkaTest {

    private static final int PARTITIONS_PER_TOPIC = 3;

    @TempDir
    static Path databaseFolder;

    @DynamicPropertySource
    static void useTemporaryDatabaseAndAnyFreePort(DynamicPropertyRegistry properties) {
        properties.add("db.path", () -> databaseFolder.resolve("test.db").toString());
        properties.add("socket.port", () -> 0);
    }

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    @Autowired
    private StationRegistry registry;

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void connectToKafka() {
        // Central's listeners start with the newest messages, so wait until they are ready.
        listeners.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(container, PARTITIONS_PER_TOPIC));

        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()),
                new StringSerializer(), new StringSerializer());
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(Topics.STATION_COMMANDS, Topics.OPERATOR_NOTIFICATIONS));
    }

    @AfterEach
    void disconnectFromKafka() {
        producer.close();
        consumer.close();
    }

    @Test
    void requestFromAnOperatorEndsWithTheStationIrrigating() {
        registry.connect("WS-01", "Parque de Canalejas");

        producer.send(new ProducerRecord<>(Topics.IRRIGATION_REQUESTS, "WS-01", """
                {"requestId":"R1","source":"OPERATOR","operatorId":"FO-001","stationId":"WS-01","limitSeconds":60}
                """));
        List<String> received = receiveUntil(message -> message.contains("AUTHORIZATION_REQUESTED"));
        assertThat(received).anyMatch(message -> message.contains("\"type\":\"START\""));

        producer.send(new ProducerRecord<>(Topics.STATION_TELEMETRY, "WS-01", """
                {"type":"COMMAND_ACK","requestId":"R1","stationId":"WS-01","command":"START","accepted":true}
                """));
        receiveUntil(message -> message.contains("\"type\":\"AUTHORIZED\""));
        assertThat(registry.find("WS-01").orElseThrow().state()).isEqualTo(StationState.IRRIGATING);
    }

    @Test
    void requestFromAnUnknownOperatorIsDenied() {
        registry.connect("WS-02", "Jardines de Abril");

        producer.send(new ProducerRecord<>(Topics.IRRIGATION_REQUESTS, "WS-02", """
                {"requestId":"R2","source":"OPERATOR","operatorId":"FO-404","stationId":"WS-02","limitSeconds":30}
                """));

        receiveUntil(message -> message.contains("\"type\":\"DENIED\"")
                && message.contains("Unknown operator FO-404"));
    }

    /** Reads commands and notifications until one matches; returns everything read. */
    private List<String> receiveUntil(Predicate<String> expected) {
        List<String> received = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            consumer.poll(Duration.ofMillis(200)).forEach(record -> received.add(record.value()));
            return received.stream().anyMatch(expected);
        });
        return received;
    }
}
