package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.Station;
import java.util.List;

/** Where stations are stored. The application does not know it is SQLite. */
public interface StationRepository {

    List<Station> findAll();

    /** Inserts the station, or updates it if it already exists. */
    void save(Station station);
}
