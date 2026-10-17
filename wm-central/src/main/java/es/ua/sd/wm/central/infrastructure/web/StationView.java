package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.domain.Station;

/** What the web panel receives for each station, as JSON. */
public record StationView(String id, String location, String state, boolean outOfService) {

    public static StationView from(Station station) {
        return new StationView(station.id(), station.location(), station.state().name(), station.outOfService());
    }
}
