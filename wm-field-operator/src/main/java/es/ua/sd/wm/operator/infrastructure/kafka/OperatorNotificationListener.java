package es.ua.sd.wm.operator.infrastructure.kafka;

import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.operator.infrastructure.console.OperatorConsole;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Muestra las notificaciones de este operario. Cada WM_FO tiene su propio grupo ("fo-FO-001"),
 * así que recibe todas las notificaciones y se queda con las que llevan su id como clave (fan-out).
 */
@Component
class OperatorNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OperatorNotificationListener.class);

    private final String operatorId;
    private final OperatorConsole console;
    private final JsonMapper json;

    OperatorNotificationListener(@Value("${operator.id}") String operatorId, OperatorConsole console, JsonMapper json) {
        this.operatorId = operatorId;
        this.console = console;
        this.json = json;
    }

    @KafkaListener(topics = Topics.OPERATOR_NOTIFICATIONS, groupId = "fo-${operator.id}")
    void receive(ConsumerRecord<String, String> record) {
        if (!operatorId.equals(record.key())) {
            return;
        }
        try {
            console.show(json.readValue(record.value(), OperatorNotification.class));
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid notification: {}", record.value());
        }
    }
}
