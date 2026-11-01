package es.ua.sd.wm.central.infrastructure.socket;

import es.ua.sd.wm.central.application.IrrigationService;
import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.common.protocol.MessageType;
import es.ua.sd.wm.common.protocol.SocketMessage;
import es.ua.sd.wm.common.validation.Validator;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conversación con un WM_WS_M conectado.
 *
 * <p>El primer mensaje tiene que ser AUTH. Después el Monitor envía HEALTH cada segundo; si no
 * llega nada durante un rato, el timeout de lectura del socket cierra la sesión. LEAK y
 * LEAK_CLEARED siempre se refieren a la estación que se autenticó en esta conexión.
 *
 * <p>Los mensajes llegan por el hilo de entrega de FrameChannel. Si un mensaje no se puede
 * leer, se lanza una excepción y FrameChannel cierra la conexión.
 */
final class MonitorSession {

    private static final Logger log = LoggerFactory.getLogger(MonitorSession.class);

    private final StationRegistry registry;
    private final IrrigationService irrigations;
    private final MonitorSessions sessions;
    private final String remoteAddress;
    // Las respuestas salen de una en una, en orden, desde un hilo propio: quien llama
    // (por ejemplo, el listener de Kafka a través de sendState) no espera al ACK.
    private final ExecutorService outbox = Executors.newSingleThreadExecutor();
    // FrameChannel empieza a leer en su constructor: puede llegar un mensaje antes de guardarlo.
    // Las respuestas esperan a este future.
    private final CompletableFuture<FrameChannel> channelReady = new CompletableFuture<>();

    // Los escribe el hilo de los mensajes; sendState los lee desde otros hilos.
    private volatile String claimedStationId;
    private volatile boolean authenticated;

    MonitorSession(Socket socket, StationRegistry registry, IrrigationService irrigations,
                   MonitorSessions sessions) throws IOException {
        this.registry = registry;
        this.irrigations = irrigations;
        this.sessions = sessions;
        this.remoteAddress = socket.getRemoteSocketAddress().toString();
        channelReady.complete(new FrameChannel(socket, this::handle, this::onClosed));
        log.info("Monitor connected from {}", remoteAddress);
    }

    void sendState(Station station) {
        if (authenticated) {
            reply(SocketMessage.of(MessageType.STATE, station.id(), station.state().name()));
        }
    }

    void close() {
        channelReady.join().close();
    }

    private void handle(String text) {
        SocketMessage message = SocketMessage.parse(text);

        if (!authenticated && message.type() != MessageType.AUTH) {
            log.warn("{} sent {} before authenticating; closing", remoteAddress, message.type());
            close();
            return;
        }

        switch (message.type()) {
            case AUTH -> authenticate(message);
            case HEALTH -> log.debug("HEALTH from {}", claimedStationId);
            case LEAK -> {
                log.warn("{} reports a LEAK", claimedStationId);
                // Pasa por IrrigationService porque, además de poner la estación en LEAK, corta su riego.
                irrigations.leakReported(claimedStationId);
            }
            case LEAK_CLEARED -> {
                log.info("{} reports the leak is fixed", claimedStationId);
                registry.clearLeak(claimedStationId);
            }
            default -> log.warn("Unexpected message from {}: {}", claimedStationId, message);
        }
    }

    private void authenticate(SocketMessage message) {
        String stationId;
        String location;
        try {
            stationId = message.field(0);
            location = message.field(1);
            Validator.requireNonEmpty(stationId, "Station id");
            Validator.requireNonEmpty(location, "Location");
        } catch (IllegalArgumentException invalid) {
            log.warn("Rejected AUTH from {}: {}", remoteAddress, invalid.getMessage());
            reply(SocketMessage.of(MessageType.AUTH_KO, "Invalid AUTH message"));
            return;
        }

        // Primero se registra esta sesión y después se cierra la anterior: así la anterior ya no
        // es la actual y, al cerrarse, no desconecta la estación.
        claimedStationId = stationId;
        MonitorSession previous = sessions.register(stationId, this);
        if (previous != null && previous != this) { // es this si llega otro AUTH por la misma conexión
            log.info("{} reconnected; closing its previous session", stationId);
            previous.close();
        }

        Station station = registry.connect(stationId, location);
        reply(SocketMessage.of(MessageType.AUTH_OK, station.id(), station.state().name()));
        // Después de AUTH_OK: así se descarta el STATE que publica connect(), y el Monitor
        // recibe primero AUTH_OK y una sola vez.
        authenticated = true;
        log.info("{} ({}) authenticated -> {}", stationId, location, station.state());
    }

    private void onClosed() {
        outbox.shutdown();
        String stationId = claimedStationId;
        if (stationId != null && sessions.removeIfCurrent(stationId, this)) {
            registry.disconnect(stationId);
            log.info("{} disconnected", stationId);
        } else {
            log.info("Connection from {} closed", remoteAddress);
        }
    }

    private void reply(SocketMessage message) {
        try {
            outbox.execute(() -> send(message));
        } catch (RejectedExecutionException sessionAlreadyClosed) {
            // Nada que hacer: el Monitor ya no está.
        }
    }

    private void send(SocketMessage message) {
        try {
            channelReady.join().send(message.format());
        } catch (IOException failed) {
            // FrameChannel se cierra solo al fallar, y eso llama a onClosed().
            log.warn("Could not send {} to {}: {}", message.type(), claimedStationId, failed.getMessage());
        }
    }
}
