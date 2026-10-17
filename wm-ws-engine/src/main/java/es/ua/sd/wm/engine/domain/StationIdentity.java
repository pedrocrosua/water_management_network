package es.ua.sd.wm.engine.domain;

import es.ua.sd.wm.common.validation.Validator;

/**
 * The Watering Station this Engine belongs to. The Engine does not receive it on the command
 * line: its Monitor assigns it when the Engine connects (ID message).
 */
public record StationIdentity(String id, String location) {

    public StationIdentity {
        Validator.requireNonEmpty(id, "Station id");
        Validator.requireNonEmpty(location, "Station location");
    }
}
