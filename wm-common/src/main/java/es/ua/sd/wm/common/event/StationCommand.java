package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.validation.Validator;

/**
 * Orden de WM_Central a una estación.
 *
 * @param requestId    id de la petición a la que pertenece la orden
 * @param type         qué debe hacer la estación
 * @param stationId    estación que debe obedecer
 * @param operatorId   operario que pidió el riego; solo en START, puede ser null
 * @param limitSeconds tiempo máximo de riego; solo en START
 * @param reason       motivo de la parada; solo en STOP
 */
public record StationCommand(
        String requestId,
        CommandType type,
        String stationId,
        String operatorId,
        Integer limitSeconds,
        EndReason reason) {

    public StationCommand {
        Validator.requireNonEmpty(requestId, "Request id");
        Validator.requireNonNull(type, "Command type");
        Validator.requireNonEmpty(stationId, "Station id");
        if (type == CommandType.START) {
            Validator.requireNonNull(limitSeconds, "Limit seconds");
            Validator.requirePositive(limitSeconds, "Limit seconds");
        }
    }
}
