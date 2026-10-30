package es.ua.sd.wm.engine.application.port.out;

import es.ua.sd.wm.common.event.StationEvent;

/** Envía lo que pasa en la estación a WM_Central (y a los operarios). */
public interface StationEventPublisher {

    void publish(StationEvent event);
}
