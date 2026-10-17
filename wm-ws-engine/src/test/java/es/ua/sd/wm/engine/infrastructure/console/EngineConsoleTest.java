package es.ua.sd.wm.engine.infrastructure.console;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.engine.domain.LeakSensor;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

/** The console menu, fed with typed lines instead of a real keyboard. */
class EngineConsoleTest {

    private final LeakSensor sensor = new LeakSensor();
    private final ByteArrayOutputStream screen = new ByteArrayOutputStream();

    @Test
    void optionOneSimulatesALeak() {
        type("1\n");

        assertThat(sensor.isLeaking()).isTrue();
        assertThat(screen.toString()).contains("LEAK simulated");
    }

    @Test
    void optionTwoRepairsTheLeak() {
        type("1\n2\n");

        assertThat(sensor.isLeaking()).isFalse();
        assertThat(screen.toString()).contains("Leak repaired");
    }

    @Test
    void unknownOptionChangesNothing() {
        type("9\n");

        assertThat(sensor.isLeaking()).isFalse();
        assertThat(screen.toString()).contains("Unknown option");
    }

    private void type(String lines) {
        new EngineConsole(sensor, new BufferedReader(new StringReader(lines)), new PrintStream(screen)).run();
    }
}
