package es.ua.sd.wm.central.infrastructure.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Sends any message to a Kafka topic as JSON text. */
@Component
class KafkaPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    KafkaPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    /** The key decides the partition: messages with the same key always keep their order. */
    void publish(String topic, String key, Object message) {
        kafka.send(topic, key, json.writeValueAsString(message));
    }
}
