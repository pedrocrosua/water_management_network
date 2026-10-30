package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.model.IrrigationSource;
import java.time.Instant;

/**
 * Un riego terminado, tal como se guarda en el historial.
 *
 * @param operatorId null si no lo pidió un operario
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
