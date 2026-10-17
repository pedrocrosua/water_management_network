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

    /** A station WM_Central has never seen before. It is disconnected until its Monitor connects. */
    public static Station register(String id, String location) {
        return new Station(id, location, StationState.DISCONNECTED, false);
    }

    /** Its Monitor has authenticated: available, unless WM_Central had blocked it. */
    public Station connected() {
        return withState(outOfService ? StationState.OUT_OF_SERVICE : StationState.AVAILABLE);
    }

    /** Its Monitor is gone: WM_Central can no longer know the real state. */
    public Station disconnected() {
        return withState(StationState.DISCONNECTED);
    }

    /** Its Engine opened the valve. Only an available station can start irrigating. */
    public Station irrigating() {
        return state == StationState.AVAILABLE ? withState(StationState.IRRIGATING) : this;
    }

    /** Its Engine reported a fault: red on the panel. */
    public Station leaking() {
        return withState(StationState.LEAK);
    }

    /** The fault is fixed. Only a station that was leaking changes. */
    public Station leakCleared() {
        return state == StationState.LEAK ? connected() : this;
    }

    /** The station reported where it is installed. */
    public Station locatedAt(String newLocation) {
        return new Station(id, newLocation, state, outOfService);
    }

    /** Same station with another state. Records are immutable, so a new one is returned. */
    public Station withState(StationState newState) {
        return new Station(id, location, newState, outOfService);
    }
}
