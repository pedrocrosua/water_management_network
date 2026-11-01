package es.ua.sd.wm.engine.infrastructure.backup;

import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.engine.application.port.out.IrrigationBackup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Guarda el último segundo del riego en un fichero JSON, que solo existe mientras hay un riego. */
public class FileIrrigationBackup implements IrrigationBackup {

    private static final Logger log = LoggerFactory.getLogger(FileIrrigationBackup.class);

    private final Path file;
    private final JsonMapper json;

    public FileIrrigationBackup(Path file, JsonMapper json) {
        this.file = file.toAbsolutePath();
        this.json = json;
    }

    @Override
    public void save(Telemetry lastSecond) {
        try {
            Files.createDirectories(file.getParent());
            // Se escribe en otro fichero y luego se renombra: si el Engine se cae a mitad, el
            // fichero bueno no queda a medias.
            Path newFile = file.resolveSibling(file.getFileName() + ".new");
            Files.writeString(newFile, json.writeValueAsString(lastSecond));
            Files.move(newFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JacksonException failed) {
            log.warn("Could not save the irrigation in {}: {}", file, failed.getMessage());
        }
    }

    @Override
    public Optional<Telemetry> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(Files.readString(file), Telemetry.class));
        } catch (IOException | JacksonException | IllegalArgumentException damaged) {
            log.warn("Ignored the damaged file {}: {}", file, damaged.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException failed) {
            log.warn("Could not delete {}: {}", file, failed.getMessage());
        }
    }
}
