package es.ua.sd.wm.central.infrastructure.persistence;

import es.ua.sd.wm.central.application.port.out.OngoingIrrigationRepository;
import es.ua.sd.wm.central.domain.OngoingIrrigation;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.model.IrrigationSource;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tabla ongoing_irrigations: una fila por riego en marcha, que se borra cuando termina. */
@Repository
class JdbcOngoingIrrigationRepository implements OngoingIrrigationRepository {

    private final JdbcClient jdbc;

    JdbcOngoingIrrigationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(OngoingIrrigation irrigation) {
        IrrigationRequest request = irrigation.request();
        jdbc.sql("""
                        INSERT INTO ongoing_irrigations (request_id, station_id, operator_id, source,
                                                         limit_seconds, started_at)
                        VALUES (:requestId, :stationId, :operatorId, :source, :limitSeconds, :startedAt)
                        """)
                .param("requestId", request.requestId())
                .param("stationId", request.stationId())
                .param("operatorId", request.operatorId())
                .param("source", request.source().name())
                .param("limitSeconds", request.limitSeconds())
                .param("startedAt", irrigation.startedAt().toString())
                .update();
    }

    @Override
    public void delete(String requestId) {
        jdbc.sql("DELETE FROM ongoing_irrigations WHERE request_id = :requestId")
                .param("requestId", requestId)
                .update();
    }

    @Override
    public List<OngoingIrrigation> findAll() {
        return jdbc.sql("""
                        SELECT request_id, station_id, operator_id, source, limit_seconds, started_at
                        FROM ongoing_irrigations
                        """)
                .query((row, rowNumber) -> {
                    IrrigationRequest request = new IrrigationRequest(row.getString("request_id"),
                            IrrigationSource.valueOf(row.getString("source")), row.getString("operator_id"),
                            row.getString("station_id"), row.getInt("limit_seconds"));
                    // 0 litros: la telemetría y el final del riego traen los datos reales.
                    return new OngoingIrrigation(request, Instant.parse(row.getString("started_at")), 0, 0);
                })
                .list();
    }
}
