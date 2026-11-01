package es.ua.sd.wm.engine.application.port.out;

import es.ua.sd.wm.common.event.Telemetry;
import java.util.Optional;

/**
 * Copia en disco del riego en curso: el último segundo regado. Si el Engine se cae, al volver
 * a arrancar sabe qué riego quedó a medias y cuánta agua llevaba.
 */
public interface IrrigationBackup {

    void save(Telemetry lastSecond);

    /** El último segundo guardado, o vacío si no quedó ningún riego a medias. */
    Optional<Telemetry> load();

    void delete();
}
