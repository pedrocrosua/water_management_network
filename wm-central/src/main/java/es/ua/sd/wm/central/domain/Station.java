package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.common.validation.Validator;

/**
 * Una Watering Station tal como la conoce WM_Central.
 *
 * @param id           id único, por ejemplo "WS-01"
 * @param location     parque o jardín donde está instalada
 * @param state        estado actual; solo lo decide WM_Central
 * @param outOfService true si WM_Central la bloqueó; se conserva al reiniciar
 */
public record Station(String id, String location, StationState state, boolean outOfService) {

    public Station {
        Validator.requireNonEmpty(id, "Station id");
        Validator.requireNonEmpty(location, "Location");
        Validator.requireNonNull(state, "State");
    }

    /** Una estación que WM_Central no había visto nunca. Está desconectada hasta que conecte su Monitor. */
    public static Station register(String id, String location) {
        return new Station(id, location, StationState.DISCONNECTED, false);
    }

    /** Su Monitor se ha autenticado. */
    public Station connected() {
        return ready();
    }

    /** Su Monitor ya no está: WM_Central no puede saber su estado real. */
    public Station disconnected() {
        return withState(StationState.DISCONNECTED);
    }

    /** Su Engine abrió la válvula. Solo empieza a regar una estación disponible. */
    public Station irrigating() {
        return state == StationState.AVAILABLE ? withState(StationState.IRRIGATING) : this;
    }

    /** Su Engine cerró la válvula. Solo cambia la estación que estaba regando. */
    public Station irrigationEnded() {
        return state == StationState.IRRIGATING ? ready() : this;
    }

    /** Su Engine avisó de una avería: rojo en el panel. */
    public Station leaking() {
        return withState(StationState.LEAK);
    }

    /** La avería está reparada. Solo cambia la estación que tenía la fuga. */
    public Station leakCleared() {
        return state == StationState.LEAK ? ready() : this;
    }

    /** La estación ha enviado dónde está instalada. */
    public Station locatedAt(String newLocation) {
        return new Station(id, newLocation, state, outOfService);
    }

    /** Lista para regar: disponible, o fuera de servicio si WM_Central la había bloqueado. */
    private Station ready() {
        return withState(outOfService ? StationState.OUT_OF_SERVICE : StationState.AVAILABLE);
    }

    /** La misma estación con otro estado. Los records no se modifican: se crea uno nuevo. */
    private Station withState(StationState newState) {
        return new Station(id, location, newState, outOfService);
    }
}
