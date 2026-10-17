package es.ua.sd.wm.operator.infrastructure.kafka;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.operator.application.port.out.IrrigationRequestSender;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Publishes the request as JSON, with the station id as key. */
public class KafkaIrrigationRequestSender implements IrrigationRequestSender {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public KafkaIrrigationRequestSender(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void send(IrrigationRequest request) {
        kafka.send(Topics.IRRIGATION_REQUESTS, request.stationId(), json.writeValueAsString(request));
    }
}
