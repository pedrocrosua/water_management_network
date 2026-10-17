package es.ua.sd.wm.engine.application.port.out;

import es.ua.sd.wm.common.event.StationEvent;

/** Sends what happens in the station to WM_Central (and to the operators). */
public interface StationEventPublisher {

    void publish(StationEvent event);
}
