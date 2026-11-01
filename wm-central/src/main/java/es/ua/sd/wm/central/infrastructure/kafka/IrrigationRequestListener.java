package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.central.application.IrrigationService;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.Topics;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee las peticiones de riego. Todas las instancias de WM_Central usan el mismo grupo
 * ("central"): si hubiera varias, Kafka les repartiría las peticiones (consumidores que compiten).
 */
@Component
class IrrigationRequestListener implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(IrrigationRequestListener.class);

    private final IrrigationService irrigations;
    private final JsonMapper json;

    IrrigationRequestListener(IrrigationService irrigations, JsonMapper json) {
        this.irrigations = irrigations;
        this.json = json;
    }

    /**
     * Al arrancar se empieza por el final del topic: las peticiones enviadas mientras WM_Central
     * estaba caída no se atienden, porque el operario ya vio "Imposible conectar con WM_Central".
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToEnd(assignments.keySet());
    }

    @KafkaListener(topics = Topics.IRRIGATION_REQUESTS, groupId = KafkaTopics.CENTRAL_GROUP)
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
