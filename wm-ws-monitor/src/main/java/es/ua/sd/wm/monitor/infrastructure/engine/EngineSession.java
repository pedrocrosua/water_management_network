package es.ua.sd.wm.monitor.infrastructure.engine;

import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.common.protocol.MessageType;
import es.ua.sd.wm.common.protocol.SocketMessage;
import es.ua.sd.wm.monitor.application.port.in.EngineEvents;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conversación con un WM_WS_E conectado.
 *
 * <p>El Engine empieza con HELLO y recibe el ID de la estación. Después recibe un PING cada
 * intervalo y responde PONG con OK o KO. Tras tres PING sin respuesta se da por perdido y se
 * cierra la conexión.
 */
final class EngineSession {

    private static final Logger log = LoggerFactory.getLogger(EngineSession.class);
    private static final int MAXIMUM_UNANSWERED_PINGS = 3;

    private final StationIdentity station;
    private final EngineEvents events;
    private final EngineSocketServer server;
    private final Duration pingInterval;
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();
    // FrameChannel empieza a leer en su constructor: puede llegar un mensaje antes de guardarlo.
    // Quien lo necesite espera a este future.
    private final CompletableFuture<FrameChannel> channelReady = new CompletableFuture<>();
    // Lo incrementa el hilo de los PING y lo pone a 0 el que recibe el PONG.
    // Con AtomicInteger no se pierde ninguna puesta a 0.
    private final AtomicInteger unansweredPings = new AtomicInteger();

    private volatile boolean introduced;
    private int nextSequence = 1;

    EngineSession(Socket socket, StationIdentity station, EngineEvents events,
                  EngineSocketServer server, Duration pingInterval) throws IOException {
        this.station = station;
        this.events = events;
        this.server = server;
        this.pingInterval = pingInterval;
        // Una red caída no envía nada, ni ACK: también se abandona tras tres intervalos en silencio.
        socket.setSoTimeout((int) pingInterval.toMillis() * MAXIMUM_UNANSWERED_PINGS);
        channelReady.complete(new FrameChannel(socket, this::handle, this::onClosed));
        log.info("Engine connected from {}", socket.getRemoteSocketAddress());
    }

    void close() {
        channelReady.join().close();
    }

    private void handle(String text) {
        SocketMessage message = SocketMessage.parse(text);

        if (!introduced && message.type() != MessageType.HELLO) {
            log.warn("Engine sent {} before HELLO; closing", message.type());
            close();
            return;
        }

        switch (message.type()) {
            case HELLO -> introduce();
            case PONG -> receivePong(message);
            // Un tipo válido que el Engine no debe enviar: se ignora.
            default -> log.warn("Unexpected message from the Engine: {}", text);
        }
    }

    private void introduce() {
        if (introduced) {
            log.warn("The Engine said HELLO twice; ignored");
            return;
        }
        EngineSession previous = server.replaceCurrentEngine(this);
        if (previous != null) {
            log.info("A new Engine connected; closing the previous one");
            previous.close();
        }

        send(SocketMessage.of(MessageType.ID, station.id(), station.location()));
        introduced = true;
        log.info("Engine identified as {}", station.id());
        pinger.scheduleAtFixedRate(this::ping, 0, pingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void ping() {
        if (unansweredPings.get() >= MAXIMUM_UNANSWERED_PINGS) {
            log.warn("The Engine did not answer {} PINGs", MAXIMUM_UNANSWERED_PINGS);
            close();
            return;
        }
        unansweredPings.incrementAndGet();
        send(SocketMessage.of(MessageType.PING, String.valueOf(nextSequence++)));
    }

    private void receivePong(SocketMessage pong) {
        unansweredPings.set(0);
        String status = pong.field(1);
        switch (status) {
            case "OK" -> events.engineAnswered(true);
            case "KO" -> events.engineAnswered(false);
            // Lanzar aquí hace que FrameChannel cierre la conexión.
            default -> throw new IllegalArgumentException("PONG status must be OK or KO, not " + status);
        }
    }

    private void onClosed() {
        pinger.shutdownNow();
        if (introduced && server.removeIfCurrent(this)) {
            log.warn("Lost the connection with the Engine");
            events.engineLost();
        } else {
            log.info("Engine connection closed");
        }
    }

    private void send(SocketMessage message) {
        try {
            channelReady.join().send(message.format());
        } catch (IOException failed) {
            // FrameChannel se cierra solo al fallar, y eso llama a onClosed().
            log.warn("Could not send {} to the Engine: {}", message.type(), failed.getMessage());
        }
    }
}
