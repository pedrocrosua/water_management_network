package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.IrrigationRecord;

/** Historial de riegos terminados. */
public interface IrrigationRepository {

    void save(IrrigationRecord irrigation);
}
