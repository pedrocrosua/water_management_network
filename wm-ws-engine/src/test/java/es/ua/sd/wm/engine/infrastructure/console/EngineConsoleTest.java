package es.ua.sd.wm.engine.infrastructure.console;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationEnded;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.StationEvent;
import es.ua.sd.wm.engine.application.IrrigationController;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The console menu, fed with typed lines instead of a real keyboard. */
class EngineConsoleTest {

    private final LeakSensor sensor = new LeakSensor();
    private final List<StationEvent> published = new ArrayList<>();
    private final IrrigationController irrigations =
            new IrrigationController(new Valve(), sensor, () -> 20.0, published::add);
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

    @Test
    void optionThreeStopsTheCurrentIrrigation() {
        irrigations.execute(new StationCommand("R1", CommandType.START, "WS-01", "FO-001", 60, null));

        type("3\n");

        assertThat(published.getLast()).isInstanceOf(IrrigationEnded.class);
    }

    @Test
    void optionThreeWithoutAnIrrigationSaysSo() {
        type("3\n");

        assertThat(screen.toString()).contains("The station is not irrigating");
    }

    private void type(String lines) {
        new EngineConsole(sensor, irrigations, new BufferedReader(new StringReader(lines)), new PrintStream(screen)).run();
    }
}
