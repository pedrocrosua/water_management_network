package es.ua.sd.wm.central.application;

import es.ua.sd.wm.central.application.port.out.IrrigationProgressPublisher;
import es.ua.sd.wm.central.application.port.out.IrrigationRepository;
import es.ua.sd.wm.central.application.port.out.OperatorNotifier;
import es.ua.sd.wm.central.application.port.out.OperatorRepository;
import es.ua.sd.wm.central.application.port.out.StationCommandSender;
import es.ua.sd.wm.central.domain.IrrigationRecord;
import es.ua.sd.wm.central.domain.Operator;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.CommandType;
import es.ua.sd.wm.common.event.IrrigationEnded;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.common.model.StationState;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decide si se autoriza una petición de riego.
 *
 * <p>Pasos: validar la petición, pedir al Engine que abra la válvula (START) y esperar su
 * respuesta. La estación solo pasa a IRRIGATING cuando el Engine confirma. Si no responde a
 * tiempo, se deniega, para que la estación no se quede esperando.
 *
 * <p>Durante el riego, la telemetría va al panel. Cuando el Engine avisa del final, la estación
 * vuelve a estar disponible, el riego se guarda y el operario recibe un resumen.
 *
 * <p>Hilos: {@link #request} lo llama el listener de peticiones; {@link #commandAnswered},
 * {@link #telemetryReceived} e {@link #irrigationEnded}, el listener de eventos de las
 * estaciones; y {@code giveUpWaiting}, el temporizador. Por eso los mapas son concurrentes.
 */
public class IrrigationService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationService.class);

    private final StationRegistry registry;
    private final OperatorRepository operators;
    private final StationCommandSender commands;
    private final OperatorNotifier notifier;
    private final IrrigationProgressPublisher progress;
    private final IrrigationRepository history;
    private final Duration answerTimeout;
    // Peticiones enviadas a una estación que esperan su respuesta; una por estación.
    private final Map<String, IrrigationRequest> waitingByStation = new ConcurrentHashMap<>();
    // Riegos en curso; uno por estación.
    private final Map<String, OngoingIrrigation> ongoingByStation = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public IrrigationService(StationRegistry registry, OperatorRepository operators,
                             StationCommandSender commands, OperatorNotifier notifier,
                             IrrigationProgressPublisher progress, IrrigationRepository history,
                             Duration answerTimeout) {
        this.registry = registry;
        this.operators = operators;
        this.commands = commands;
        this.notifier = notifier;
        this.progress = progress;
        this.history = history;
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

        commands.send(startCommand(request));
        tell(request, NotificationType.AUTHORIZATION_REQUESTED, "Waiting for " + stationId + " to open its valve");
        // El temporizador no se cancela: si la respuesta ya llegó, giveUpWaiting no encuentra nada que quitar.
        timer.schedule(() -> giveUpWaiting(request), answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Respuesta del Engine a una orden. De momento solo importan las de START. */
    public void commandAnswered(CommandAck answer) {
        if (answer.command() != CommandType.START) {
            return;
        }
        IrrigationRequest request = removeWaitingRequest(answer.stationId(), answer.requestId());
        if (request == null) {
            closeValveIfOpenedLate(answer);
            return;
        }
        if (!answer.accepted()) {
            deny(request, answer.reason());
            return;
        }

        // Nunca está vacío: las estaciones no se borran del registro.
        Station station = registry.startIrrigation(answer.stationId()).orElseThrow();
        if (station.state() != StationState.IRRIGATING) {
            // Algo pasó mientras se esperaba (por ejemplo, una fuga): hay que volver a cerrar la válvula.
            commands.send(stopCommand(answer));
            deny(request, answer.stationId() + " is no longer available (" + station.state() + ")");
            return;
        }
        ongoingByStation.put(answer.stationId(), new OngoingIrrigation(request, Instant.now()));
        log.info("{} is irrigating for request {}", answer.stationId(), answer.requestId());
        tell(request, NotificationType.AUTHORIZED, answer.stationId() + " is irrigating");
    }

    /** Un segundo de riego. Se ignora la telemetría de un riego que WM_Central no conoce. */
    public void telemetryReceived(Telemetry telemetry) {
        if (findOngoing(telemetry.stationId(), telemetry.requestId()) != null) {
            progress.publish(telemetry);
        }
    }

    /** El Engine cerró la válvula: se libera la estación, se guarda el riego y se avisa al operario. */
    public void irrigationEnded(IrrigationEnded ended) {
        OngoingIrrigation irrigation = findOngoing(ended.stationId(), ended.requestId());
        // remove(clave, valor) solo quita este riego: el mismo riego nunca se cierra dos veces.
        if (irrigation == null || !ongoingByStation.remove(ended.stationId(), irrigation)) {
            log.info("{} ended request {}, which was not in progress here", ended.stationId(), ended.requestId());
            return;
        }
        registry.endIrrigation(ended.stationId());

        IrrigationRequest request = irrigation.request();
        history.save(new IrrigationRecord(ended.stationId(), request.operatorId(), request.source(),
                irrigation.startedAt(), Instant.now(), request.limitSeconds(), ended.totalLitres(), ended.reason()));
        // Locale.ROOT: los decimales siempre con punto (12.5 L), sea cual sea el idioma del sistema.
        String message = String.format(Locale.ROOT, "%s finished (%s): %.1f L in %d s",
                ended.stationId(), ended.reason(), ended.totalLitres(), ended.durationSeconds());
        log.info(message);
        tell(request, NotificationType.IRRIGATION_ENDED, message, ended);
    }

    /** Para el temporizador al apagar WM_Central. */
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

    /** La estación no respondió a tiempo. Solo deniega si la petición sigue esperando. */
    private void giveUpWaiting(IrrigationRequest request) {
        if (waitingByStation.remove(request.stationId(), request)) {
            deny(request, request.stationId() + " did not answer");
        }
    }

    /** Quita la petición que esperaba esta respuesta. Devuelve null si ya no estaba. */
    private IrrigationRequest removeWaitingRequest(String stationId, String requestId) {
        IrrigationRequest waiting = waitingByStation.get(stationId);
        if (waiting == null || !waiting.requestId().equals(requestId)) {
            return null;
        }
        // La respuesta y el temporizador compiten por quitarla: solo uno de los dos lo consigue.
        boolean removedHere = waitingByStation.remove(stationId, waiting);
        return removedHere ? waiting : null;
    }

    /** Si el Engine abrió la válvula para una petición ya denegada, se le ordena cerrarla. */
    private void closeValveIfOpenedLate(CommandAck answer) {
        if (answer.accepted()) {
            log.warn("{} accepted request {} too late; sending STOP", answer.stationId(), answer.requestId());
            commands.send(stopCommand(answer));
        }
    }

    /** Devuelve el riego en curso de esa petición, o null si no lo hay. */
    private OngoingIrrigation findOngoing(String stationId, String requestId) {
        OngoingIrrigation irrigation = ongoingByStation.get(stationId);
        if (irrigation == null || !irrigation.request().requestId().equals(requestId)) {
            return null;
        }
        return irrigation;
    }

    private static StationCommand startCommand(IrrigationRequest request) {
        // El último campo (motivo de parada) solo se usa en STOP.
        return new StationCommand(request.requestId(), CommandType.START, request.stationId(),
                request.operatorId(), request.limitSeconds(), null);
    }

    private static StationCommand stopCommand(CommandAck answer) {
        // STOP no lleva operario ni tiempo límite; tampoco motivo, de momento.
        return new StationCommand(answer.requestId(), CommandType.STOP, answer.stationId(), null, null, null);
    }

    private void deny(IrrigationRequest request, String reason) {
        log.info("Request {} denied: {}", request.requestId(), reason);
        tell(request, NotificationType.DENIED, reason);
    }

    private void tell(IrrigationRequest request, NotificationType type, String message) {
        tell(request, type, message, null);
    }

    /** Avisa al operario de la petición. Si la petición no viene de un operario, no se avisa a nadie. */
    private void tell(IrrigationRequest request, NotificationType type, String message, IrrigationEnded summary) {
        if (request.operatorId() != null) {
            notifier.notify(new OperatorNotification(request.requestId(), request.operatorId(),
                    request.stationId(), type, message, summary));
        }
    }

    /** Una petición autorizada con la válvula abierta, y cuándo se abrió. */
    private record OngoingIrrigation(IrrigationRequest request, Instant startedAt) {
    }
}
