package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.central.application.IrrigationService;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads irrigation requests. Every WM_Central instance uses the same group ("central"), so if
 * there were several, Kafka would share the requests between them (competing consumers).
 */
@Component
class IrrigationRequestListener {

    private static final Logger log = LoggerFactory.getLogger(IrrigationRequestListener.class);

    private final IrrigationService irrigations;
    private final JsonMapper json;

    IrrigationRequestListener(IrrigationService irrigations, JsonMapper json) {
        this.irrigations = irrigations;
        this.json = json;
    }

    @KafkaListener(topics = Topics.IRRIGATION_REQUESTS, groupId = "central")
    void receive(String message) {
        IrrigationRequest request;
        try {
            request = json.readValue(message, IrrigationRequest.class);
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid irrigation request: {}", message);
            return;
        }
        irrigations.request(request);
    }
}
