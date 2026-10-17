package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.IrrigationRecord;

/** History of finished irrigations. */
public interface IrrigationRepository {

    void save(IrrigationRecord irrigation);
}
