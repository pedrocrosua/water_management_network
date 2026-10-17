package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.Station;

/** Tells the outside world (for now, the web panel) that a station has changed. */
public interface StationChangePublisher {

    void publish(Station station);
}
