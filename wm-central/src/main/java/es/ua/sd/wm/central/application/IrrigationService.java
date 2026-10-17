package es.ua.sd.wm.central.application;

import es.ua.sd.wm.central.application.port.out.OperatorNotifier;
import es.ua.sd.wm.central.application.port.out.OperatorRepository;
import es.ua.sd.wm.central.application.port.out.StationCommandSender;
import es.ua.sd.wm.central.domain.Operator;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.model.StationState;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether an irrigation request is authorized.
 *
 * <p>Steps: validate the request, ask the station's Engine to open its valve (START) and wait
 * for its answer. Only when the Engine confirms does the station become IRRIGATING. If the
 * Engine does not answer in time, the request is denied so the station is not stuck waiting.
 */
public class IrrigationService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationService.class);

    private final StationRegistry registry;
    private final OperatorRepository operators;
    private final StationCommandSender commands;
    private final OperatorNotifier notifier;
    private final Duration answerTimeout;
    // Requests already sent to a station and waiting for its answer, one per station.
    private final Map<String, IrrigationRequest> waitingByStation = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public IrrigationService(StationRegistry registry, OperatorRepository operators,
                             StationCommandSender commands, OperatorNotifier notifier, Duration answerTimeout) {
        this.registry = registry;
        this.operators = operators;
        this.commands = commands;
        this.notifier = notifier;
        this.answerTimeout = answerTimeout;
    }

    public void request(IrrigationRequest request) {
        String stationId = request.stationId();
        log.info("Irrigation request {} for {} ({} s)", request.requestId(), stationId, request.limitSeconds());
        tell(request, NotificationType.RECEIVED,
                "Request received for " + stationId + " (" + request.limitSeconds() + " s)");
        tell(request, NotificationType.VALIDATING, "Checking the station and the operator");

        Optional<String> reasonToDeny = findReasonToDeny(request);
        if (reasonToDeny.isPresent()) {
            deny(request, reasonToDeny.get());
            return;
        }
        if (waitingByStation.putIfAbsent(stationId, request) != null) {
            deny(request, stationId + " is already handling another request");
            return;
        }

        commands.send(new StationCommand(request.requestId(), CommandType.START, stationId,
                request.operatorId(), request.limitSeconds(), null));
        tell(request, NotificationType.AUTHORIZATION_REQUESTED, "Waiting for " + stationId + " to open its valve");
        timer.schedule(() -> giveUpWaiting(request), answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** The Engine answered an order. Only START answers matter for now. */
    public void commandAnswered(CommandAck answer) {
        if (answer.command() != CommandType.START) {
            return;
        }
        IrrigationRequest request = removeWaitingRequest(answer.stationId(), answer.requestId());
        if (request == null) {
            stopLateIrrigation(answer);
            return;
        }
        if (!answer.accepted()) {
            deny(request, answer.reason());
            return;
        }

        Station station = registry.startIrrigation(answer.stationId()).orElseThrow();
        if (station.state() != StationState.IRRIGATING) {
            // Something happened while waiting (for example a leak): the valve must close again.
            commands.send(stopCommand(answer));
            deny(request, answer.stationId() + " is no longer available (" + station.state() + ")");
            return;
        }
        log.info("{} is irrigating for request {}", answer.stationId(), answer.requestId());
        tell(request, NotificationType.AUTHORIZED, answer.stationId() + " is irrigating");
    }

    public void stop() {
        timer.shutdownNow();
    }

    private Optional<String> findReasonToDeny(IrrigationRequest request) {
        Optional<Station> station = registry.find(request.stationId());
        if (station.isEmpty()) {
            return Optional.of("Unknown station " + request.stationId());
        }
        if (request.operatorId() != null) {
            Optional<Operator> operator = operators.find(request.operatorId());
            if (operator.isEmpty()) {
                return Optional.of("Unknown operator " + request.operatorId());
            }
            if (!operator.get().active()) {
                return Optional.of("Operator " + request.operatorId() + " is not active");
            }
        }
        StationState state = station.get().state();
        if (state != StationState.AVAILABLE) {
            return Optional.of(request.stationId() + " is not available (" + state + ")");
        }
        return Optional.empty();
    }

    private void giveUpWaiting(IrrigationRequest request) {
        if (waitingByStation.remove(request.stationId(), request)) {
            deny(request, request.stationId() + " did not answer");
        }
    }

    private IrrigationRequest removeWaitingRequest(String stationId, String requestId) {
        IrrigationRequest waiting = waitingByStation.get(stationId);
        if (waiting != null && waiting.requestId().equals(requestId) && waitingByStation.remove(stationId, waiting)) {
            return waiting;
        }
        return null;
    }

    /** The Engine opened its valve for a request that was already denied: close it. */
    private void stopLateIrrigation(CommandAck answer) {
        if (answer.accepted()) {
            log.warn("{} accepted request {} too late; sending STOP", answer.stationId(), answer.requestId());
            commands.send(stopCommand(answer));
        }
    }

    private static StationCommand stopCommand(CommandAck answer) {
        return new StationCommand(answer.requestId(), CommandType.STOP, answer.stationId(), null, null, null);
    }

    private void deny(IrrigationRequest request, String reason) {
        log.info("Request {} denied: {}", request.requestId(), reason);
        tell(request, NotificationType.DENIED, reason);
    }

    /** Requests that do not come from an operator have nobody to notify. */
    private void tell(IrrigationRequest request, NotificationType type, String message) {
        if (request.operatorId() != null) {
            notifier.notify(new OperatorNotification(request.requestId(), request.operatorId(),
                    request.stationId(), type, message, null));
        }
    }
}
