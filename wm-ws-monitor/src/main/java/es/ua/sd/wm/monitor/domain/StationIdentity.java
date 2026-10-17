package es.ua.sd.wm.monitor.domain;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Which Watering Station this Monitor watches over.
 *
 * @param id       unique id given on the command line, for example "WS-01"
 * @param location park or garden, sent to WM_Central when registering
 */
public record StationIdentity(String id, String location) {

    public StationIdentity {
        Validator.requireNonEmpty(id, "Station id (--station.id)");
        Validator.requireNonEmpty(location, "Station location (--station.location)");
    }
}
