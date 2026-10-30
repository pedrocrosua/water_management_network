package es.ua.sd.wm.monitor.domain;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Qué Watering Station vigila este Monitor.
 *
 * @param id       id único que llega por línea de comandos, por ejemplo "WS-01"
 * @param location parque o jardín; se envía a WM_Central al registrarse
 */
public record StationIdentity(String id, String location) {

    public StationIdentity {
        Validator.requireNonEmpty(id, "Station id (--station.id)");
        Validator.requireNonEmpty(location, "Station location (--station.location)");
    }
}
