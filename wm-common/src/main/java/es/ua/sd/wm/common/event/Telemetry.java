package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Lectura del caudalímetro; se envía cada segundo mientras la estación riega.
 *
 * @param flowLitresPerMinute caudal actual
 * @param accumulatedLitres   agua suministrada desde que empezó el riego
 * @param elapsedSeconds      tiempo desde que empezó el riego
 */
public record Telemetry(
        String requestId,
        String stationId,
        String operatorId,
        double flowLitresPerMinute,
        double accumulatedLitres,
        long elapsedSeconds) implements StationEvent {

    public Telemetry {
        Validator.requireNonEmpty(requestId, "Request id");
        Validator.requireNonEmpty(stationId, "Station id");
    }
}
