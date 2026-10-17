package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Flow meter reading, sent every second while the station is irrigating.
 *
 * @param flowLitresPerMinute current flow
 * @param accumulatedLitres   water supplied since the irrigation started
 * @param elapsedSeconds      time since the irrigation started
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
