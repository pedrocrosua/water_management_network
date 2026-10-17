package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.validation.Validator;

/**
 * An order from WM_Central to one station.
 *
 * @param requestId    id of the request this order belongs to
 * @param type         what the station must do
 * @param stationId    station that must obey
 * @param operatorId   operator who asked for the irrigation; only for START, may be null
 * @param limitSeconds maximum irrigation time; only for START
 * @param reason       why the irrigation is stopped; only for STOP
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
