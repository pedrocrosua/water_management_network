package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Tells one operator how their request is going.
 *
 * @param message text to show on the operator's screen
 * @param summary final summary; only present when type is IRRIGATION_ENDED
 */
public record OperatorNotification(
        String requestId,
        String operatorId,
        String stationId,
        NotificationType type,
        String message,
        IrrigationEnded summary) {

    public OperatorNotification {
        Validator.requireNonEmpty(requestId, "Request id");
        Validator.requireNonEmpty(operatorId, "Operator id");
        Validator.requireNonNull(type, "Notification type");
    }
}
