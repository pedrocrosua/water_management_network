package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.validation.Validator;

/**
 * Asks WM_Central to start an irrigation.
 *
 * @param requestId    unique id; every later message about this request carries it
 * @param source       who is asking
 * @param operatorId   the operator's id; null when the request does not come from an operator
 * @param stationId    station that should irrigate
 * @param limitSeconds maximum irrigation time
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
