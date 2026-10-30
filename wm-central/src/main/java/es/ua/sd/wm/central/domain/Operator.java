package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Un operario de campo registrado en WM_Central.
 *
 * @param active false si ya no puede pedir riegos
 */
public record Operator(String id, String name, boolean active) {

    public Operator {
        Validator.requireNonEmpty(id, "Operator id");
        Validator.requireNonEmpty(name, "Operator name");
    }
}
