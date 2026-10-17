package es.ua.sd.wm.central.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.central.application.port.out.OperatorNotifier;
import es.ua.sd.wm.central.application.port.out.OperatorRepository;
import es.ua.sd.wm.central.application.port.out.StationCommandSender;
import es.ua.sd.wm.central.application.port.out.StationRepository;
import es.ua.sd.wm.central.domain.Operator;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.model.StationState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The irrigation request flow with in-memory fakes instead of Kafka and SQLite. */
class IrrigationServiceTest {

    private final Map<String, Operator> operators = new HashMap<>();
    private final List<StationCommand> sentCommands = new CopyOnWriteArrayList<>();
    private final List<OperatorNotification> notifications = new CopyOnWriteArrayList<>();
    private StationRegistry registry;
    private IrrigationService service;

    @BeforeEach
    void createService() {
        registry = new StationRegistry(new InMemoryStations(), List.of());
        registry.connect("WS-01", "Canalejas");
        operators.put("FO-001", new Operator("FO-001", "Ana", true));
        OperatorRepository operatorRepository = id -> Optional.ofNullable(operators.get(id));
        StationCommandSender commandSender = sentCommands::add;
        OperatorNotifier notifier = notifications::add;
        service = new IrrigationService(registry, operatorRepository, commandSender, notifier,
                Duration.ofMillis(300));
    }

    @AfterEach
    void stopService() {
        service.stop();
    }

    @Test
    void validRequestAsksTheStationToStartAndTellsTheOperatorEachStep() {
        service.request(request("R1", "FO-001", "WS-01"));

        assertThat(notificationTypes()).containsExactly(
                NotificationType.RECEIVED, NotificationType.VALIDATING, NotificationType.AUTHORIZATION_REQUESTED);
        assertThat(sentCommands).singleElement().satisfies(command -> {
            assertThat(command.type()).isEqualTo(CommandType.START);
            assertThat(command.stationId()).isEqualTo("WS-01");
            assertThat(command.operatorId()).isEqualTo("FO-001");
            assertThat(command.limitSeconds()).isEqualTo(60);
        });
    }

    @Test
    void unknownStationIsDenied() {
        service.request(request("R1", "FO-001", "WS-99"));

        assertDeniedWith("Unknown station WS-99");
    }

    @Test
    void unknownOperatorIsDenied() {
        service.request(request("R1", "FO-009", "WS-01"));

        assertDeniedWith("Unknown operator FO-009");
    }

    @Test
    void inactiveOperatorIsDenied() {
        operators.put("FO-002", new Operator("FO-002", "Luis", false));

        service.request(request("R1", "FO-002", "WS-01"));

        assertDeniedWith("Operator FO-002 is not active");
    }

    @Test
    void stationThatIsNotAvailableIsDenied() {
        registry.reportLeak("WS-01");

        service.request(request("R1", "FO-001", "WS-01"));

        assertDeniedWith("WS-01 is not available (LEAK)");
    }

    @Test
    void secondRequestWhileTheStationIsAnsweringTheFirstIsDenied() {
        service.request(request("R1", "FO-001", "WS-01"));

        service.request(request("R2", "FO-001", "WS-01"));

        assertDeniedWith("WS-01 is already handling another request");
        assertThat(sentCommands).hasSize(1);
    }

    @Test
    void acceptedStartMakesTheStationIrrigateAndAuthorizesTheOperator() {
        service.request(request("R1", "FO-001", "WS-01"));

        service.commandAnswered(new CommandAck("R1", "WS-01", CommandType.START, true, null));

        assertThat(registry.find("WS-01").orElseThrow().state()).isEqualTo(StationState.IRRIGATING);
        assertThat(notifications.getLast().type()).isEqualTo(NotificationType.AUTHORIZED);
    }

    @Test
    void rejectedStartIsDeniedWithTheStationReason() {
        service.request(request("R1", "FO-001", "WS-01"));

        service.commandAnswered(new CommandAck("R1", "WS-01", CommandType.START, false, "The station has a leak"));

        assertDeniedWith("The station has a leak");
        assertThat(registry.find("WS-01").orElseThrow().state()).isEqualTo(StationState.AVAILABLE);
    }

    @Test
    void stationThatDoesNotAnswerIsDeniedAndCanBeAskedAgain() {
        service.request(request("R1", "FO-001", "WS-01"));

        await().atMost(Duration.ofSeconds(2))
                .until(() -> notifications.getLast().type() == NotificationType.DENIED);
        assertThat(notifications.getLast().message()).isEqualTo("WS-01 did not answer");

        service.request(request("R2", "FO-001", "WS-01"));
        assertThat(sentCommands).hasSize(2);
    }

    @Test
    void lateAcceptedStartIsStoppedAtOnce() {
        service.commandAnswered(new CommandAck("R-OLD", "WS-01", CommandType.START, true, null));

        assertThat(sentCommands).singleElement().satisfies(command -> {
            assertThat(command.type()).isEqualTo(CommandType.STOP);
            assertThat(command.requestId()).isEqualTo("R-OLD");
        });
        assertThat(registry.find("WS-01").orElseThrow().state()).isEqualTo(StationState.AVAILABLE);
    }

    private static IrrigationRequest request(String requestId, String operatorId, String stationId) {
        return new IrrigationRequest(requestId, IrrigationSource.OPERATOR, operatorId, stationId, 60);
    }

    private List<NotificationType> notificationTypes() {
        return notifications.stream().map(OperatorNotification::type).toList();
    }

    private void assertDeniedWith(String reason) {
        assertThat(notifications.getLast().type()).isEqualTo(NotificationType.DENIED);
        assertThat(notifications.getLast().message()).isEqualTo(reason);
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
