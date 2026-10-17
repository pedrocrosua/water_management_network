package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.common.validation.Validator;

/** Current state of one station, so operators can see which ones are available. */
public record StationStatus(
        String stationId,
        String location,
        StationState state) {

    public StationStatus {
        Validator.requireNonEmpty(stationId, "Station id");
        Validator.requireNonNull(state, "State");
    }
}
