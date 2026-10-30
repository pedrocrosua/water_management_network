package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.Operator;
import java.util.Optional;

/** Dónde se guardan los operarios registrados. */
public interface OperatorRepository {

    Optional<Operator> find(String operatorId);
}
