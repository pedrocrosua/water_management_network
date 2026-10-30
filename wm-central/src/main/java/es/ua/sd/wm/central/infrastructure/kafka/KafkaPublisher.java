package es.ua.sd.wm.central.infrastructure.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Envía cualquier mensaje a un topic de Kafka como texto JSON. */
@Component
class KafkaPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    KafkaPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    /** La clave decide la partición: los mensajes con la misma clave mantienen su orden. */
    void publish(String topic, String key, Object message) {
        kafka.send(topic, key, json.writeValueAsString(message));
    }
}
