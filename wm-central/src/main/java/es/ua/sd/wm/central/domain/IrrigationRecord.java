package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.model.IrrigationSource;
import java.time.Instant;

/**
 * A finished irrigation, as it is stored in the history.
 *
 * @param operatorId null when the irrigation was not requested by an operator
 */
public record IrrigationRecord(
        String stationId,
        String operatorId,
        IrrigationSource source,
        Instant startedAt,
        Instant endedAt,
        int limitSeconds,
        double totalLitres,
        EndReason endReason) {
}
