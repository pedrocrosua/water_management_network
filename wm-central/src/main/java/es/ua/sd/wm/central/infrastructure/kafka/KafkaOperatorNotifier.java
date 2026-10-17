package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.central.application.port.out.OperatorNotifier;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.Topics;
import org.springframework.stereotype.Component;

/** Notifications go to every WM_FO; each one keeps those whose key is its own operator id. */
@Component
class KafkaOperatorNotifier implements OperatorNotifier {

    private final KafkaPublisher publisher;

    KafkaOperatorNotifier(KafkaPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void notify(OperatorNotification notification) {
        publisher.publish(Topics.OPERATOR_NOTIFICATIONS, notification.operatorId(), notification);
    }
}
