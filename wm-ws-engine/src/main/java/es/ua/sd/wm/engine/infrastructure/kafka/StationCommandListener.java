package es.ua.sd.wm.engine.infrastructure.kafka;

import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.Topics;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the orders for this station from Kafka.
 *
 * <p>It cannot start with the application: the Engine only learns its station id when the
 * Monitor sends it. Then it starts with its own group ("engine-WS-01"), so it receives every
 * order of the topic and keeps only those whose key is its station id (fan-out).
 */
public class StationCommandListener {

    private static final Logger log = LoggerFactory.getLogger(StationCommandListener.class);

    private final ConsumerFactory<String, String> consumerFactory;
    private final Consumer<StationCommand> commandHandler;
    private final JsonMapper json;

    private KafkaMessageListenerContainer<String, String> container;
    private String stationId;

    public StationCommandListener(ConsumerFactory<String, String> consumerFactory,
                                  Consumer<StationCommand> commandHandler, JsonMapper json) {
        this.consumerFactory = consumerFactory;
        this.commandHandler = commandHandler;
        this.json = json;
    }

    /** Starts reading the orders of this station. Nothing changes if it already does. */
    public synchronized void listenFor(String newStationId) {
        if (newStationId.equals(stationId)) {
            return;
        }
        stop();
        ContainerProperties properties = new ContainerProperties(Topics.STATION_COMMANDS);
        properties.setGroupId("engine-" + newStationId);
        properties.setMessageListener((MessageListener<String, String>) record -> receive(newStationId, record));

        container = new KafkaMessageListenerContainer<>(consumerFactory, properties);
        container.start();
        stationId = newStationId;
        log.info("Listening for orders to {}", newStationId);
    }

    public synchronized void stop() {
        if (container != null) {
            container.stop();
            container = null;
            stationId = null;
        }
    }

    private void receive(String ownStationId, ConsumerRecord<String, String> record) {
        if (!ownStationId.equals(record.key())) {
            return;
        }
        StationCommand command;
        try {
            command = json.readValue(record.value(), StationCommand.class);
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid order: {}", record.value());
            return;
        }
        commandHandler.accept(command);
    }
}
