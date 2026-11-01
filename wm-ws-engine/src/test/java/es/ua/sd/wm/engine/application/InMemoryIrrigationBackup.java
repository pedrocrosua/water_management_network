package es.ua.sd.wm.engine.application;

import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.engine.application.port.out.IrrigationBackup;
import java.util.Optional;

/** Copia del riego para los tests: en lugar de un fichero, un campo. */
public class InMemoryIrrigationBackup implements IrrigationBackup {

    Telemetry saved;

    @Override
    public void save(Telemetry lastSecond) {
        saved = lastSecond;
    }

    @Override
    public Optional<Telemetry> load() {
        return Optional.ofNullable(saved);
    }

    @Override
    public void delete() {
        saved = null;
    }
}
