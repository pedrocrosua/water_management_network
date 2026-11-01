package es.ua.sd.wm.engine.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El riego manual desde el menú de la estación, con una lista en memoria en lugar de Kafka. */
class ManualIrrigationTest {

    private final LeakSensor sensor = new LeakSensor();
    private final IrrigationController irrigations =
            new IrrigationController(new Valve(), sensor, () -> 20.0, event -> { });
    private final List<IrrigationRequest> sentRequests = new ArrayList<>();
    private final ManualIrrigation manual = new ManualIrrigation(irrigations, sensor, sentRequests::add);

    @Test
    void manualIrrigationIsRequestedToCentralLikeAnOperatorWould() {
        manual.stationIdentified("WS-01");

        String answer = manual.request(30);

        assertThat(answer).isEqualTo("Manual irrigation of 30 s requested to WM_Central");
        assertThat(sentRequests).singleElement().satisfies(request -> {
            assertThat(request.source()).isEqualTo(IrrigationSource.STATION);
            assertThat(request.stationId()).isEqualTo("WS-01");
            assertThat(request.operatorId()).isNull();
            assertThat(request.limitSeconds()).isEqualTo(30);
        });
    }

    @Test
    void nothingIsRequestedBeforeTheMonitorSaysWhichStationThisIs() {
        assertThat(manual.request(30)).isEqualTo("The Engine does not know its station yet: is WM_WS_M running?");
        assertThat(sentRequests).isEmpty();
    }

    @Test
    void nothingIsRequestedWhileTheStationHasALeakOrIsIrrigating() {
        manual.stationIdentified("WS-01");
        sensor.simulateLeak();

        assertThat(manual.request(30)).isEqualTo("The station has a leak");

        sensor.repair();
        irrigations.execute(new StationCommand("R1", CommandType.START, "WS-01", "FO-001", 60, null));

        assertThat(manual.request(30)).isEqualTo("The station is already irrigating");
        assertThat(sentRequests).isEmpty();
    }

    @Test
    void nothingIsRequestedWhileTheMonitorIsLost() {
        manual.stationIdentified("WS-01");
        irrigations.monitorLost();

        assertThat(manual.request(30)).isEqualTo("Averiada: no irrigation is accepted until WM_WS_M comes back");
        assertThat(sentRequests).isEmpty();
    }
}
