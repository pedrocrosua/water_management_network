package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.domain.Station;

/** Lo que recibe el panel web por cada estación, en JSON. */
public record StationView(String id, String location, String state, boolean outOfService) {

    public static StationView from(Station station) {
        return new StationView(station.id(), station.location(), station.state().name(), station.outOfService());
    }
}
