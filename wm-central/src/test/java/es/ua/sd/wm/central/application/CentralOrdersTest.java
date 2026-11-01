package es.ua.sd.wm.central.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.ua.sd.wm.central.application.port.out.StationRepository;
import es.ua.sd.wm.central.domain.IrrigationRecord;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationEnded;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.model.StationState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Órdenes del panel de WM_Central (CU-10 y CU-11), con dobles en memoria en lugar de Kafka y SQLite. */
class CentralOrdersTest {

    private final List<StationCommand> sentCommands = new CopyOnWriteArrayList<>();
    private final List<IrrigationRecord> savedIrrigations = new CopyOnWriteArrayList<>();
    private StationRegistry registry;
    private IrrigationService irrigations;
    private CentralOrders orders;

    @BeforeEach
    void createOrders() {
        registry = new StationRegistry(new InMemoryStations(), List.of());
        registry.connect("WS-01", "Canalejas");
        registry.connect("WS-02", "Abril");
        // Las órdenes de Central no llevan operario: no hace falta ni buscarlo ni avisarle.
        irrigations = new IrrigationService(registry, id -> Optional.empty(), sentCommands::add,
                notification -> { }, telemetry -> { }, savedIrrigations::add, new InMemoryOngoingIrrigations(),
                Duration.ofSeconds(5));
        orders = new CentralOrders(registry, irrigations);
    }

    @AfterEach
    void stopService() {
        irrigations.stop();
    }

    @Test
    void irrigationFromCentralGoesLikeAnOperatorRequestButWithoutOperator() {
        orders.irrigate("WS-01", 30);

        StationCommand start = sentCommands.getFirst();
        assertThat(start.type()).isEqualTo(CommandType.START);
        assertThat(start.stationId()).isEqualTo("WS-01");
        assertThat(start.operatorId()).isNull();
        assertThat(start.limitSeconds()).isEqualTo(30);

        irrigations.commandAnswered(new CommandAck(start.requestId(), "WS-01", CommandType.START, true, null));
        irrigations.irrigationEnded(new IrrigationEnded(start.requestId(), "WS-01", null, 10.0, 30, EndReason.TIME_LIMIT));

        assertThat(savedIrrigations).singleElement().satisfies(saved -> {
            assertThat(saved.source()).isEqualTo(IrrigationSource.CENTRAL);
            assertThat(saved.operatorId()).isNull();
            assertThat(saved.endReason()).isEqualTo(EndReason.TIME_LIMIT);
        });
    }

    @Test
    void irrigateAllOnlyAsksTheAvailableStations() {
        registry.reportLeak("WS-02");

        int asked = orders.irrigateAll(30);

        assertThat(asked).isEqualTo(1);
        assertThat(sentCommands).extracting(StationCommand::stationId).containsExactly("WS-01");
    }

    @Test
    void blockAllAndActivateAllChangeEveryStation() {
        assertThat(orders.blockAll()).isEqualTo(2);
        assertThat(registry.findAll()).extracting(Station::state).containsOnly(StationState.OUT_OF_SERVICE);

        assertThat(orders.activateAll()).isEqualTo(2);
        assertThat(registry.findAll()).extracting(Station::state).containsOnly(StationState.AVAILABLE);
    }

    @Test
    void orderForAnUnknownStationIsRejected() {
        assertThatThrownBy(() -> orders.block("WS-99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown station WS-99");
        assertThat(sentCommands).isEmpty();
    }

    private static final class InMemoryStations implements StationRepository {

        private final List<Station> stations = new ArrayList<>();

        @Override
        public List<Station> findAll() {
            return List.copyOf(stations);
        }

        @Override
        public void save(Station station) {
            stations.removeIf(stored -> stored.id().equals(station.id()));
            stations.add(station);
        }
    }
}
