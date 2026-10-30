package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.validation.Validator;

/** La válvula se ha cerrado y el riego ha terminado. También es el resumen que recibe el operario. */
public record IrrigationEnded(
        String requestId,
        String stationId,
        String operatorId,
        double totalLitres,
        long durationSeconds,
        EndReason reason) implements StationEvent {

    public IrrigationEnded {
        Validator.requireNonEmpty(requestId, "Request id");
        Validator.requireNonEmpty(stationId, "Station id");
        Validator.requireNonNull(reason, "End reason");
    }
}
