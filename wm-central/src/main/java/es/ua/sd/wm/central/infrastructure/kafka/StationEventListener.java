package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.central.application.IrrigationService;
import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.IrrigationEnded;
import es.ua.sd.wm.common.event.StationEvent;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Lee lo que informan los Engines. El campo "type" del JSON indica el tipo de evento. */
@Component
class StationEventListener {

    private static final Logger log = LoggerFactory.getLogger(StationEventListener.class);

    private final IrrigationService irrigations;
    private final JsonMapper json;

    StationEventListener(IrrigationService irrigations, JsonMapper json) {
        this.irrigations = irrigations;
        this.json = json;
    }

    @KafkaListener(topics = Topics.STATION_TELEMETRY, groupId = KafkaTopics.CENTRAL_GROUP)
    void receive(String message) {
        StationEvent event;
        try {
            event = json.readValue(message, StationEvent.class);
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid station event: {}", message);
            return;
        }
        switch (event) {
            case CommandAck answer -> irrigations.commandAnswered(answer);
            case Telemetry telemetry -> irrigations.telemetryReceived(telemetry);
            case IrrigationEnded ended -> irrigations.irrigationEnded(ended);
        }
    }
}
