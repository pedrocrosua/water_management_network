package es.ua.sd.wm.central.domain;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.Telemetry;
import java.time.Instant;

/**
 * Un riego autorizado con la válvula abierta: la petición, cuándo empezó y lo que lleva regado
 * según el último segundo que informó el Engine.
 */
public record OngoingIrrigation(IrrigationRequest request, Instant startedAt, double litres, long seconds) {

    /** El mismo riego con los datos de un segundo más. Los records no se modifican: se crea uno nuevo. */
    public OngoingIrrigation withProgress(Telemetry telemetry) {
        return new OngoingIrrigation(request, startedAt, telemetry.accumulatedLitres(), telemetry.elapsedSeconds());
    }
}
