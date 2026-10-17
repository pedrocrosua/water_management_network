package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.validation.Validator;

/**
 * A field operator registered in WM_Central.
 *
 * @param active false when the operator may no longer request irrigations
 */
public record Operator(String id, String name, boolean active) {

    public Operator {
        Validator.requireNonEmpty(id, "Operator id");
        Validator.requireNonEmpty(name, "Operator name");
    }
}
