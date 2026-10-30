package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.common.validation.Validator;

/** Estado actual de una estación, para que los operarios vean cuáles están disponibles. */
public record StationStatus(
        String stationId,
        String location,
        StationState state) {

    public StationStatus {
        Validator.requireNonEmpty(stationId, "Station id");
        Validator.requireNonNull(state, "State");
    }
}
