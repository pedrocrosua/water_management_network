package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.common.validation.Validator;

/**
 * A Watering Station as WM_Central knows it.
 *
 * @param id           unique id, for example "WS-01"
 * @param location     park or garden where it is installed
 * @param state        current state, decided only by WM_Central
 * @param outOfService true when WM_Central blocked it; survives restarts
 */
public record Station(String id, String location, StationState state, boolean outOfService) {

    public Station {
        Validator.requireNonEmpty(id, "Station id");
        Validator.requireNonEmpty(location, "Location");
        Validator.requireNonNull(state, "State");
    }

    /** Same station with another state. Records are immutable, so a new one is returned. */
    public Station withState(StationState newState) {
        return new Station(id, location, newState, outOfService);
    }
}
