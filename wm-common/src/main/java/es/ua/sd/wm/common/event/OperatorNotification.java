package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Le cuenta a un operario cómo va su petición.
 *
 * @param message texto que se muestra en la pantalla del operario
 * @param summary resumen final; solo cuando type es IRRIGATION_ENDED
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
