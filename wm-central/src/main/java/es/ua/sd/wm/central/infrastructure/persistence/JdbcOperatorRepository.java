package es.ua.sd.wm.central.infrastructure.persistence;

import es.ua.sd.wm.central.application.port.out.OperatorRepository;
import es.ua.sd.wm.central.domain.Operator;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads operators from the SQLite "operators" table. */
@Repository
class JdbcOperatorRepository implements OperatorRepository {

    private final JdbcClient jdbc;

    JdbcOperatorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Operator> find(String operatorId) {
        return jdbc.sql("SELECT id, name, active FROM operators WHERE id = :id")
                .param("id", operatorId)
                .query((row, rowNumber) -> new Operator(
                        row.getString("id"),
                        row.getString("name"),
                        row.getInt("active") == 1))
                .optional();
    }
}
