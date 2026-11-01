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
import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.model.StationState;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decide si se autoriza una petición de riego y sigue cada riego hasta que termina.
 *
 * <p>Pasos: validar la petición, pedir al Engine que abra la válvula (START) y esperar su
 * respuesta. La estación solo pasa a IRRIGATING cuando el Engine confirma. Si no responde a
 * tiempo, se deniega, para que la estación no se quede esperando.
 *
 * <p>Durante el riego, la telemetría va al panel. Cuando el Engine avisa del final, la estación
 * vuelve a estar disponible, el riego se guarda y el operario recibe un resumen. Si aparece una
 * fuga, WM_Central corta el riego sin esperar al Engine.
 *
 * <p>Hilos: llaman a esta clase los listeners de Kafka, el temporizador y los Monitores (cuando
 * avisan de una fuga). Los métodos que usan los mapas son synchronized, o solo se llaman desde uno
 * que lo es: se ejecutan de uno en uno, así que ninguno ve un riego a medio cambiar.
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
    private final Map<String, IrrigationRequest> waitingByStation = new HashMap<>();
    // Riegos en curso; uno por estación.
    private final Map<String, OngoingIrrigation> ongoingByStation = new HashMap<>();
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

    public synchronized void request(IrrigationRequest request) {
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
        if (waitingByStation.containsKey(stationId)) {
            deny(request, stationId + " is already handling another request");
            return;
        }

        waitingByStation.put(stationId, request);
        commands.send(startCommand(request));
        tell(request, NotificationType.AUTHORIZATION_REQUESTED, "Waiting for " + stationId + " to open its valve");
        // El temporizador no se cancela: si la respuesta ya llegó, giveUpWaiting no encuentra la petición.
        timer.schedule(() -> giveUpWaiting(request), answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Respuesta del Engine a una orden. De momento solo importan las de START. */
    public synchronized void commandAnswered(CommandAck answer) {
        if (answer.command() != CommandType.START) {
            return;
        }
        IrrigationRequest request = findWaiting(answer.stationId(), answer.requestId());
        if (request == null) {
            closeValveIfOpenedLate(answer);
            return;
        }
        waitingByStation.remove(answer.stationId());
        if (!answer.accepted()) {
            deny(request, answer.reason());
            return;
        }

        // Nunca está vacío: las estaciones no se borran del registro.
        Station station = registry.startIrrigation(answer.stationId()).orElseThrow();
        if (station.state() != StationState.IRRIGATING) {
            // Algo pasó mientras se esperaba (por ejemplo, una fuga): hay que volver a cerrar la válvula.
            commands.send(stopCommand(answer.requestId(), answer.stationId(), null));
            deny(request, answer.stationId() + " is no longer available (" + station.state() + ")");
            return;
        }
        // Empieza con 0 litros en 0 segundos; la telemetría lo irá actualizando.
        ongoingByStation.put(answer.stationId(), new OngoingIrrigation(request, Instant.now(), 0, 0));
        log.info("{} is irrigating for request {}", answer.stationId(), answer.requestId());
        tell(request, NotificationType.AUTHORIZED, answer.stationId() + " is irrigating");
    }

    /** Un segundo de riego. Se ignora la telemetría de un riego que WM_Central no conoce. */
    public synchronized void telemetryReceived(Telemetry telemetry) {
        OngoingIrrigation irrigation = findOngoing(telemetry.stationId(), telemetry.requestId());
        if (irrigation == null) {
            return;
        }
        // Se apunta lo que lleva regado: si una fuga corta el riego, es lo que se guarda.
        ongoingByStation.put(telemetry.stationId(), irrigation.withProgress(telemetry));
        progress.publish(telemetry);
    }

    /** El Engine cerró la válvula: se libera la estación, se guarda el riego y se avisa al operario. */
    public synchronized void irrigationEnded(IrrigationEnded ended) {
        OngoingIrrigation irrigation = findOngoing(ended.stationId(), ended.requestId());
        if (irrigation == null) {
            // Por ejemplo, el aviso del Engine después de que una fuga cortara el riego.
            log.info("{} ended request {}, which was not in progress here", ended.stationId(), ended.requestId());
            return;
        }
        ongoingByStation.remove(ended.stationId());
        registry.endIrrigation(ended.stationId());
        saveAndNotify(irrigation, ended);
    }

    /**
     * El Monitor avisa de una fuga: la estación pasa a LEAK y, si estaba regando, el riego se
     * corta. No se espera al Engine, porque puede estar caído: se guarda lo que llevaba regado.
     */
    public synchronized void leakReported(String stationId) {
        registry.reportLeak(stationId);
        OngoingIrrigation irrigation = ongoingByStation.remove(stationId);
        if (irrigation == null) {
            return; // no estaba regando
        }
        IrrigationRequest request = irrigation.request();
        log.warn("Leak in {}: stopping request {}", stationId, request.requestId());
        commands.send(stopCommand(request.requestId(), stationId, EndReason.LEAK));
        IrrigationEnded ended = new IrrigationEnded(request.requestId(), stationId, request.operatorId(),
                irrigation.litres(), irrigation.seconds(), EndReason.LEAK);
        saveAndNotify(irrigation, ended);
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
    private synchronized void giveUpWaiting(IrrigationRequest request) {
        if (findWaiting(request.stationId(), request.requestId()) != null) {
            waitingByStation.remove(request.stationId());
            deny(request, request.stationId() + " did not answer");
        }
    }

    /** Devuelve la petición que espera esa respuesta, o null si no la hay. */
    private IrrigationRequest findWaiting(String stationId, String requestId) {
        IrrigationRequest waiting = waitingByStation.get(stationId);
        if (waiting == null || !waiting.requestId().equals(requestId)) {
            return null;
        }
        return waiting;
    }

    /** Si el Engine abrió la válvula para una petición ya denegada, se le ordena cerrarla. */
    private void closeValveIfOpenedLate(CommandAck answer) {
        if (answer.accepted()) {
            log.warn("{} accepted request {} too late; sending STOP", answer.stationId(), answer.requestId());
            commands.send(stopCommand(answer.requestId(), answer.stationId(), null));
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

    /** Guarda el riego terminado y envía el resumen al operario. */
    private void saveAndNotify(OngoingIrrigation irrigation, IrrigationEnded ended) {
        IrrigationRequest request = irrigation.request();
        history.save(new IrrigationRecord(ended.stationId(), request.operatorId(), request.source(),
                irrigation.startedAt(), Instant.now(), request.limitSeconds(), ended.totalLitres(), ended.reason()));
        // Locale.ROOT: los decimales siempre con punto (12.5 L), sea cual sea el idioma del sistema.
        String message = String.format(Locale.ROOT, "%s finished (%s): %.1f L in %d s",
                ended.stationId(), ended.reason(), ended.totalLitres(), ended.durationSeconds());
        log.info(message);
        tell(request, NotificationType.IRRIGATION_ENDED, message, ended);
    }

    private static StationCommand startCommand(IrrigationRequest request) {
        // El último campo (motivo de parada) solo se usa en STOP.
        return new StationCommand(request.requestId(), CommandType.START, request.stationId(),
                request.operatorId(), request.limitSeconds(), null);
    }

    /** STOP no lleva operario ni tiempo límite. Sin motivo (null), el Engine lo cuenta como parada manual. */
    private static StationCommand stopCommand(String requestId, String stationId, EndReason reason) {
        return new StationCommand(requestId, CommandType.STOP, stationId, null, null, reason);
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

    /**
     * Un riego autorizado con la válvula abierta: la petición, cuándo empezó y lo que lleva regado
     * según el último segundo que informó el Engine.
     */
    private record OngoingIrrigation(IrrigationRequest request, Instant startedAt, double litres, long seconds) {

        /** El mismo riego con los datos de un segundo más. Los records no se modifican: se crea uno nuevo. */
        OngoingIrrigation withProgress(Telemetry telemetry) {
            return new OngoingIrrigation(request, startedAt, telemetry.accumulatedLitres(), telemetry.elapsedSeconds());
        }
    }
}
