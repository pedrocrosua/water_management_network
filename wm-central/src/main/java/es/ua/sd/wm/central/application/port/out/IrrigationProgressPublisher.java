package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.common.event.Telemetry;

/** Muestra cómo va un riego (caudal, agua, tiempo) mientras dura. No se guarda nada. */
public interface IrrigationProgressPublisher {

    void publish(Telemetry telemetry);
}
