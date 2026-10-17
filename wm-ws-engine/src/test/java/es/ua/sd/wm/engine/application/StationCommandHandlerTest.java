package es.ua.sd.wm.engine.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.StationEvent;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the Engine does with each order, with an in-memory list instead of Kafka. */
class StationCommandHandlerTest {

    private final Valve valve = new Valve();
    private final LeakSensor sensor = new LeakSensor();
    private final List<StationEvent> published = new ArrayList<>();
    private final StationCommandHandler handler = new StationCommandHandler(valve, sensor, published::add);

    @Test
    void startOpensTheValveAndIsAccepted() {
        handler.execute(start("R1"));

        assertThat(valve.isOpen()).isTrue();
        assertThat(published).containsExactly(new CommandAck("R1", "WS-01", CommandType.START, true, null));
    }

    @Test
    void startIsRejectedWhileThereIsALeak() {
        sensor.simulateLeak();

        handler.execute(start("R1"));

        assertThat(valve.isOpen()).isFalse();
        assertThat(published).containsExactly(
                new CommandAck("R1", "WS-01", CommandType.START, false, "The station has a leak"));
    }

    @Test
    void startIsRejectedWhileAlreadyIrrigating() {
        handler.execute(start("R1"));

        handler.execute(start("R2"));

        assertThat(published.getLast()).isEqualTo(
                new CommandAck("R2", "WS-01", CommandType.START, false, "The station is already irrigating"));
    }

    @Test
    void stopClosesTheValve() {
        handler.execute(start("R1"));

        handler.execute(new StationCommand("R1", CommandType.STOP, "WS-01", null, null, null));

        assertThat(valve.isOpen()).isFalse();
        assertThat(published.getLast()).isEqualTo(new CommandAck("R1", "WS-01", CommandType.STOP, true, null));
    }

    private static StationCommand start(String requestId) {
        return new StationCommand(requestId, CommandType.START, "WS-01", "FO-001", 60, null);
    }
}
