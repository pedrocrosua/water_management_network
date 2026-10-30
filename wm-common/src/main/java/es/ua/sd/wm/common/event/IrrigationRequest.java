package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.validation.Validator;

/**
 * Pide a WM_Central que empiece un riego.
 *
 * @param requestId    id único; lo llevan todos los mensajes posteriores de esta petición
 * @param source       quién lo pide
 * @param operatorId   id del operario; null si no lo pide un operario
 * @param stationId    estación que debe regar
 * @param limitSeconds tiempo máximo de riego
 */
public record IrrigationRequest(
        String requestId,
        IrrigationSource source,
        String operatorId,
        String stationId,
        int limitSeconds) {

    public IrrigationRequest {
        Validator.requireNonEmpty(requestId, "Request id");
        Validator.requireNonNull(source, "Source");
        Validator.requireNonEmpty(stationId, "Station id");
        Validator.requirePositive(limitSeconds, "Limit seconds");
        if (source == IrrigationSource.OPERATOR) {
            Validator.requireNonEmpty(operatorId, "Operator id");
        }
    }
}
