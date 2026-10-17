package es.ua.sd.wm.central.infrastructure.socket;

import es.ua.sd.wm.central.application.StationRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Socket server where every WM_WS_M connects. One thread accepts connections; each connection
 * becomes a {@link MonitorSession}.
 */
@Component
public class StationSocketServer implements SmartLifecycle {

    /** A Monitor sends HEALTH every second: 3 seconds of silence means it is gone. */
    static final int SILENCE_TIMEOUT_MILLIS = 3000;

    private static final Logger log = LoggerFactory.getLogger(StationSocketServer.class);

    private final StationRegistry registry;
    private final MonitorSessions sessions;
    private final int configuredPort;

    private volatile ServerSocket serverSocket;

    StationSocketServer(StationRegistry registry, MonitorSessions sessions,
                        @Value("${socket.port}") int configuredPort) {
        this.registry = registry;
        this.sessions = sessions;
        this.configuredPort = configuredPort;
    }

    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(configuredPort);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot listen on socket port " + configuredPort, exception);
        }
        Thread.ofPlatform().name("station-socket-server").start(this::acceptConnections);
        log.info("Waiting for WM_WS_M connections on port {}", getPort());
    }

    @Override
    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
        sessions.closeAll();
    }

    @Override
    public boolean isRunning() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    /** The real port; useful when the configured one is 0 (any free port, used by tests). */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptConnections() {
        while (isRunning()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(SILENCE_TIMEOUT_MILLIS);
                new MonitorSession(socket, registry, sessions);
            } catch (IOException exception) {
                if (isRunning()) {
                    log.warn("Could not accept a connection: {}", exception.getMessage());
                }
            }
        }
    }
}
