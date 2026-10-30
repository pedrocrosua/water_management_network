package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.Station;
import java.util.List;

/** Dónde se guardan las estaciones. La aplicación no sabe que es SQLite. */
public interface StationRepository {

    List<Station> findAll();

    /** Inserta la estación, o la actualiza si ya existe. */
    void save(Station station);
}
