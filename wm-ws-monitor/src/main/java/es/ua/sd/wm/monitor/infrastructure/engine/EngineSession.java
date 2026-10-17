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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conversation with one connected WM_WS_E.
 *
 * <p>The Engine starts with HELLO and receives the station's ID. From then on it gets a PING
 * every interval and must answer PONG with OK or KO. After three PINGs without an answer the
 * Engine is considered lost and the connection is closed.
 */
final class EngineSession {

    private static final Logger log = LoggerFactory.getLogger(EngineSession.class);
    private static final int MAXIMUM_UNANSWERED_PINGS = 3;

    private final StationIdentity station;
    private final EngineEvents events;
    private final EngineSocketServer server;
    private final Duration pingInterval;
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();
    // FrameChannel starts reading inside its own constructor, so a message can arrive before
    // the channel is stored. Whoever needs it waits for this future.
    private final CompletableFuture<FrameChannel> channelReady = new CompletableFuture<>();

    private volatile boolean introduced;
    private volatile int unansweredPings;
    private int nextSequence = 1;

    EngineSession(Socket socket, StationIdentity station, EngineEvents events,
                  EngineSocketServer server, Duration pingInterval) throws IOException {
        this.station = station;
        this.events = events;
        this.server = server;
        this.pingInterval = pingInterval;
        // A dead network sends nothing, not even ACKs: also give up after three silent intervals.
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
            default -> log.warn("Unexpected message from the Engine: {}", text);
        }
    }

    private void introduce() {
        if (introduced) {
            log.warn("The Engine said HELLO twice; ignored");
            return;
        }
        EngineSession previous = server.replaceCurrentEngine(this);
        if (previous != null && previous != this) {
            log.info("A new Engine connected; closing the previous one");
            previous.close();
        }

        send(SocketMessage.of(MessageType.ID, station.id(), station.location()));
        introduced = true;
        log.info("Engine identified as {}", station.id());
        pinger.scheduleAtFixedRate(this::ping, 0, pingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void ping() {
        if (unansweredPings >= MAXIMUM_UNANSWERED_PINGS) {
            log.warn("The Engine did not answer {} PINGs", MAXIMUM_UNANSWERED_PINGS);
            close();
            return;
        }
        unansweredPings++;
        send(SocketMessage.of(MessageType.PING, String.valueOf(nextSequence++)));
    }

    private void receivePong(SocketMessage pong) {
        unansweredPings = 0;
        String status = pong.field(1);
        switch (status) {
            case "OK" -> events.engineAnswered(true);
            case "KO" -> events.engineAnswered(false);
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
            // FrameChannel closes itself on failure, which triggers onClosed().
            log.warn("Could not send {} to the Engine: {}", message.type(), failed.getMessage());
        }
    }
}
