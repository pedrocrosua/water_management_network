package es.ua.sd.wm.central.infrastructure.persistence;

import es.ua.sd.wm.central.application.port.out.IrrigationRepository;
import es.ua.sd.wm.central.domain.IrrigationRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Writes finished irrigations into the SQLite "irrigations" table. Dates are ISO-8601 text. */
@Repository
class JdbcIrrigationRepository implements IrrigationRepository {

    private final JdbcClient jdbc;

    JdbcIrrigationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(IrrigationRecord irrigation) {
        jdbc.sql("""
                        INSERT INTO irrigations (station_id, operator_id, source, started_at, ended_at,
                                                 limit_seconds, total_litres, end_reason)
                        VALUES (:stationId, :operatorId, :source, :startedAt, :endedAt,
                                :limitSeconds, :totalLitres, :endReason)
                        """)
                .param("stationId", irrigation.stationId())
                .param("operatorId", irrigation.operatorId())
                .param("source", irrigation.source().name())
                .param("startedAt", irrigation.startedAt().toString())
                .param("endedAt", irrigation.endedAt().toString())
                .param("limitSeconds", irrigation.limitSeconds())
                .param("totalLitres", irrigation.totalLitres())
                .param("endReason", irrigation.endReason().name())
                .update();
    }
}
