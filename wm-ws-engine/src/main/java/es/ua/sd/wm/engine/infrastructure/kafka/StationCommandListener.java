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
 * Lee de Kafka las órdenes para esta estación.
 *
 * <p>No puede arrancar con la aplicación: el Engine solo conoce su id de estación cuando el
 * Monitor se lo envía. Entonces arranca con su propio grupo ("engine-WS-01"), así que recibe
 * todas las órdenes del topic y se queda solo con las que llevan su id como clave (fan-out).
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

    /** Empieza a leer las órdenes de esta estación. Si ya lo hacía, no cambia nada. */
    public synchronized void listenFor(String newStationId) {
        if (newStationId.equals(stationId)) {
            return;
        }
        stop();
        ContainerProperties properties = new ContainerProperties(Topics.STATION_COMMANDS);
        properties.setGroupId("engine-" + newStationId);
        // Se usa newStationId y no el campo stationId, que stop() pone a null.
        MessageListener<String, String> listener = record -> receive(newStationId, record);
        properties.setMessageListener(listener);

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
