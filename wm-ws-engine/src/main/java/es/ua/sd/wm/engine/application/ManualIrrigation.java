package es.ua.sd.wm.engine.application;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.engine.application.port.out.IrrigationRequestSender;
import es.ua.sd.wm.engine.domain.LeakSensor;
import java.util.UUID;

/**
 * Riego manual desde el menú de la estación. No abre la válvula: pide el riego a WM_Central
 * (origen STATION), que lo valida como cualquier otro. Si lo autoriza, llega el START de
 * siempre; si no, llega la respuesta DENIED.
 */
public class ManualIrrigation {

    private final IrrigationController irrigations;
    private final LeakSensor sensor;
    private final IrrigationRequestSender requests;
    // Lo dice el Monitor al conectar; hasta entonces el Engine no sabe qué estación es.
    private String stationId;

    public ManualIrrigation(IrrigationController irrigations, LeakSensor sensor, IrrigationRequestSender requests) {
        this.irrigations = irrigations;
        this.sensor = sensor;
        this.requests = requests;
    }

    public synchronized void stationIdentified(String newStationId) {
        stationId = newStationId;
    }

    /** Pide el riego y devuelve el texto que muestra el menú. */
    public synchronized String request(int seconds) {
        if (stationId == null) {
            return "The Engine does not know its station yet: is WM_WS_M running?";
        }
        if (sensor.isLeaking()) {
            return "The station has a leak";
        }
        if (irrigations.isIrrigating()) {
            return "The station is already irrigating";
        }
        String requestId = UUID.randomUUID().toString();
        requests.send(new IrrigationRequest(requestId, IrrigationSource.STATION, null, stationId, seconds));
        return "Manual irrigation of " + seconds + " s requested to WM_Central";
    }
}
