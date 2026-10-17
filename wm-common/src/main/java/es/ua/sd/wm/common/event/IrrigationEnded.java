package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.validation.Validator;

/** The valve is closed and the irrigation is over. It is also the summary sent to the operator. */
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
