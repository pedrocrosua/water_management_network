package es.ua.sd.wm.engine.infrastructure.kafka;

import es.ua.sd.wm.common.event.StationEvent;
import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.engine.application.port.out.StationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Publica los eventos de la estación en JSON, con el id de la estación como clave. */
public class KafkaStationEventPublisher implements StationEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public KafkaStationEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publish(StationEvent event) {
        // writerFor conserva el campo "type", que indica al lector qué tipo de evento es.
        String message = json.writerFor(StationEvent.class).writeValueAsString(event);
        kafka.send(Topics.STATION_TELEMETRY, event.stationId(), message);
    }
}
