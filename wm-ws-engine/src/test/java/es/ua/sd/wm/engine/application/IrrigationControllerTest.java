package es.ua.sd.wm.engine.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationEnded;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.StationEvent;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Engine's irrigation logic, with a fixed flow and an in-memory list instead of Kafka. */
class IrrigationControllerTest {

    private static final double FLOW_LITRES_PER_MINUTE = 30.0;

    private final Valve valve = new Valve();
    private final LeakSensor sensor = new LeakSensor();
    private final List<StationEvent> published = new ArrayList<>();
    private final IrrigationController controller =
            new IrrigationController(valve, sensor, () -> FLOW_LITRES_PER_MINUTE, published::add);

    @Test
    void startOpensTheValveAndIsAccepted() {
        controller.execute(start("R1", 60));

        assertThat(valve.isOpen()).isTrue();
        assertThat(published).containsExactly(new CommandAck("R1", "WS-01", CommandType.START, true, null));
    }

    @Test
    void startIsRejectedWhileThereIsALeak() {
        sensor.simulateLeak();

        controller.execute(start("R1", 60));

        assertThat(valve.isOpen()).isFalse();
        assertThat(published).containsExactly(
                new CommandAck("R1", "WS-01", CommandType.START, false, "The station has a leak"));
    }

    @Test
    void startIsRejectedWhileAlreadyIrrigating() {
        controller.execute(start("R1", 60));

        controller.execute(start("R2", 60));

        assertThat(published.getLast()).isEqualTo(
                new CommandAck("R2", "WS-01", CommandType.START, false, "The station is already irrigating"));
    }

    @Test
    void everySecondOfIrrigationPublishesTheFlowAndTheWaterSoFar() {
        controller.execute(start("R1", 60));

        controller.tick();
        controller.tick();

        assertThat(published.getLast()).isEqualTo(new Telemetry("R1", "WS-01", "FO-001", 30.0, 1.0, 2));
    }

    @Test
    void reachingTheTimeLimitClosesTheValveAndPublishesTheSummary() {
        controller.execute(start("R1", 3));

        controller.tick();
        controller.tick();
        controller.tick();

        assertThat(valve.isOpen()).isFalse();
        assertThat(published.getLast()).isEqualTo(
                new IrrigationEnded("R1", "WS-01", "FO-001", 1.5, 3, EndReason.TIME_LIMIT));
    }

    @Test
    void stoppingFromTheStationMenuEndsTheIrrigation() {
        controller.execute(start("R1", 60));
        controller.tick();

        boolean stopped = controller.stopFromStation();

        assertThat(stopped).isTrue();
        assertThat(valve.isOpen()).isFalse();
        assertThat(published.getLast()).isEqualTo(
                new IrrigationEnded("R1", "WS-01", "FO-001", 0.5, 1, EndReason.MANUAL_STOP));
    }

    @Test
    void stoppingFromTheMenuWithoutAnIrrigationDoesNothing() {
        assertThat(controller.stopFromStation()).isFalse();
        assertThat(published).isEmpty();
    }

    @Test
    void stopOrderEndsTheIrrigationWithTheGivenReason() {
        controller.execute(start("R1", 60));

        controller.execute(new StationCommand("R1", CommandType.STOP, "WS-01", null, null, EndReason.LEAK));

        assertThat(valve.isOpen()).isFalse();
        assertThat(published).contains(
                new IrrigationEnded("R1", "WS-01", "FO-001", 0.0, 0, EndReason.LEAK),
                new CommandAck("R1", "WS-01", CommandType.STOP, true, null));
    }

    @Test
    void nothingIsPublishedWhileNotIrrigating() {
        controller.tick();

        assertThat(published).isEmpty();
    }

    private static StationCommand start(String requestId, int limitSeconds) {
        return new StationCommand(requestId, CommandType.START, "WS-01", "FO-001", limitSeconds, null);
    }
}
