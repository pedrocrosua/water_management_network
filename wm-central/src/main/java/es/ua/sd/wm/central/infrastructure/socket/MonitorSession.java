package es.ua.sd.wm.central.infrastructure.socket;

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
 * Conversation with one connected WM_WS_M.
 *
 * <p>The first message must be AUTH. After that the Monitor sends HEALTH every second; the
 * socket's read timeout closes the session if nothing arrives for a while. LEAK and
 * LEAK_CLEARED always refer to the station that authenticated on this connection.
 */
final class MonitorSession {

    private static final Logger log = LoggerFactory.getLogger(MonitorSession.class);

    private final StationRegistry registry;
    private final MonitorSessions sessions;
    private final String remoteAddress;
    // Replies are sent one by one, in order, without blocking the thread that reads messages.
    private final ExecutorService outbox = Executors.newSingleThreadExecutor();
    // FrameChannel starts reading inside its own constructor, so a message can arrive before
    // the channel field below is ready. Replies wait for this future instead.
    private final CompletableFuture<FrameChannel> channelReady = new CompletableFuture<>();

    private volatile String claimedStationId;
    private volatile boolean authenticated;

    MonitorSession(Socket socket, StationRegistry registry, MonitorSessions sessions) throws IOException {
        this.registry = registry;
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
                registry.reportLeak(claimedStationId);
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

        // Register before closing an older session, so the old one knows it is no longer current.
        claimedStationId = stationId;
        MonitorSession previous = sessions.register(stationId, this);
        if (previous != null && previous != this) {
            log.info("{} reconnected; closing its previous session", stationId);
            previous.close();
        }

        Station station = registry.connect(stationId, location);
        reply(SocketMessage.of(MessageType.AUTH_OK, station.id(), station.state().name()));
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
            // Nothing to do: the Monitor is gone.
        }
    }

    private void send(SocketMessage message) {
        try {
            channelReady.join().send(message.format());
        } catch (IOException failed) {
            // FrameChannel closes itself on failure, which triggers onClosed().
            log.warn("Could not send {} to {}: {}", message.type(), claimedStationId, failed.getMessage());
        }
    }
}
