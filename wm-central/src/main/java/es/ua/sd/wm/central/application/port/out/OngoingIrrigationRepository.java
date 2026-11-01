package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.OngoingIrrigation;
import java.util.List;

/** Los riegos en curso, guardados para no perderlos si WM_Central se cae. */
public interface OngoingIrrigationRepository {

    void save(OngoingIrrigation irrigation);

    void delete(String requestId);

    /** Los riegos que quedaron en marcha; los litros los irá trayendo la telemetría. */
    List<OngoingIrrigation> findAll();
}
