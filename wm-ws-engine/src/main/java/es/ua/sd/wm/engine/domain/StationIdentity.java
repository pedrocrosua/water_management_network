package es.ua.sd.wm.engine.domain;

import es.ua.sd.wm.common.validation.Validator;

/**
 * La Watering Station a la que pertenece este Engine. No llega por línea de comandos: la asigna
 * su Monitor cuando el Engine se conecta (mensaje ID).
 */
public record StationIdentity(String id, String location) {

    public StationIdentity {
        Validator.requireNonEmpty(id, "Station id");
        Validator.requireNonEmpty(location, "Station location");
    }
}
