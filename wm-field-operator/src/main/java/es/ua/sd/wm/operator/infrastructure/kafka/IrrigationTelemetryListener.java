package es.ua.sd.wm.operator.infrastructure.kafka;

import es.ua.sd.wm.common.event.StationEvent;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.operator.infrastructure.console.OperatorConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shows, second by second, the irrigations this operator asked for. The key of the topic is the
 * station id, so the operator is recognised by the operatorId inside each message.
 */
@Component
class IrrigationTelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(IrrigationTelemetryListener.class);

    private final String operatorId;
    private final OperatorConsole console;
    private final JsonMapper json;

    IrrigationTelemetryListener(@Value("${operator.id}") String operatorId, OperatorConsole console, JsonMapper json) {
        this.operatorId = operatorId;
        this.console = console;
        this.json = json;
    }

    @KafkaListener(topics = Topics.STATION_TELEMETRY, groupId = "fo-${operator.id}")
    void receive(String message) {
        StationEvent event;
        try {
            event = json.readValue(message, StationEvent.class);
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid station event: {}", message);
            return;
        }
        if (event instanceof Telemetry telemetry && operatorId.equals(telemetry.operatorId())) {
            console.showTelemetry(telemetry);
        }
    }
}
