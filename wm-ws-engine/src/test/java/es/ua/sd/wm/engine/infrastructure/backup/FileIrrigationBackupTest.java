package es.ua.sd.wm.engine.infrastructure.backup;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.Telemetry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** La copia del riego en un fichero de verdad, dentro de una carpeta temporal. */
class FileIrrigationBackupTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @TempDir
    Path folder;

    @Test
    void theSavedSecondIsReadAgainAfterARestart() {
        Path file = folder.resolve("data/irrigation.json");
        new FileIrrigationBackup(file, json).save(lastSecond());

        // Un objeto nuevo, como el de un Engine que acaba de volver a arrancar.
        FileIrrigationBackup afterRestart = new FileIrrigationBackup(file, json);

        assertThat(afterRestart.load()).contains(lastSecond());
    }

    @Test
    void afterDeletingItThereIsNothingToRecover() {
        FileIrrigationBackup backup = new FileIrrigationBackup(folder.resolve("irrigation.json"), json);
        backup.save(lastSecond());

        backup.delete();

        assertThat(backup.load()).isEmpty();
    }

    @Test
    void aDamagedFileIsIgnored() throws IOException {
        Path file = folder.resolve("irrigation.json");
        Files.writeString(file, "{\"type\":\"TELEMETRY\",\"requestId\":");

        assertThat(new FileIrrigationBackup(file, json).load()).isEmpty();
    }

    private static Telemetry lastSecond() {
        return new Telemetry("R1", "WS-01", "FO-001", 20.0, 3.5, 7);
    }
}
