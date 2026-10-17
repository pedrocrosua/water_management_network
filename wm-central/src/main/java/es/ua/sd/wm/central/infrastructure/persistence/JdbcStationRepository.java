package es.ua.sd.wm.central.infrastructure.persistence;

import es.ua.sd.wm.central.application.port.out.StationRepository;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.model.StationState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stores stations in the SQLite "stations" table using plain SQL. */
@Repository
class JdbcStationRepository implements StationRepository {

    private final JdbcClient jdbc;

    JdbcStationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Station> findAll() {
        return jdbc.sql("SELECT id, location, state, out_of_service FROM stations ORDER BY id")
                .query(JdbcStationRepository::toStation)
                .list();
    }

    @Override
    public void save(Station station) {
        jdbc.sql("""
                        INSERT INTO stations (id, location, state, out_of_service)
                        VALUES (:id, :location, :state, :outOfService)
                        ON CONFLICT (id) DO UPDATE SET
                            location = excluded.location,
                            state = excluded.state,
                            out_of_service = excluded.out_of_service
                        """)
                .param("id", station.id())
                .param("location", station.location())
                .param("state", station.state().name())
                .param("outOfService", station.outOfService() ? 1 : 0)
                .update();
    }

    private static Station toStation(ResultSet row, int rowNumber) throws SQLException {
        return new Station(
                row.getString("id"),
                row.getString("location"),
                StationState.valueOf(row.getString("state")),
                row.getInt("out_of_service") == 1);
    }
}
