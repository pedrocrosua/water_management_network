package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.Operator;
import java.util.Optional;

/** Where the registered operators are stored. */
public interface OperatorRepository {

    Optional<Operator> find(String operatorId);
}
