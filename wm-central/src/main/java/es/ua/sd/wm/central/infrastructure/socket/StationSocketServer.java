package es.ua.sd.wm.central.infrastructure.socket;

import es.ua.sd.wm.central.application.IrrigationService;
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
 * Servidor de sockets al que se conecta cada WM_WS_M. Un hilo acepta las conexiones y cada una
 * se convierte en una {@link MonitorSession}.
 *
 * <p>Spring llama a start() al arrancar y a stop() al apagar (SmartLifecycle).
 */
@Component
public class StationSocketServer implements SmartLifecycle {

    /** Un Monitor envía HEALTH cada segundo: 3 segundos de silencio significan que se ha ido. */
    static final int SILENCE_TIMEOUT_MILLIS = 3000;

    private static final Logger log = LoggerFactory.getLogger(StationSocketServer.class);

    private final StationRegistry registry;
    private final IrrigationService irrigations;
    private final MonitorSessions sessions;
    private final int configuredPort;

    private volatile ServerSocket serverSocket;

    StationSocketServer(StationRegistry registry, IrrigationService irrigations, MonitorSessions sessions,
                        @Value("${socket.port}") int configuredPort) {
        this.registry = registry;
        this.irrigations = irrigations;
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
            // Ya estaba cerrado.
        }
        sessions.closeAll();
    }

    @Override
    public boolean isRunning() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    /** El puerto real; útil cuando el configurado es 0 (cualquier puerto libre, en los tests). */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptConnections() {
        while (isRunning()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(SILENCE_TIMEOUT_MILLIS);
                // No se guarda: la sesión vive en los hilos de su FrameChannel y, tras el AUTH,
                // queda registrada en MonitorSessions.
                new MonitorSession(socket, registry, irrigations, sessions);
            } catch (IOException exception) {
                if (isRunning()) {
                    log.warn("Could not accept a connection: {}", exception.getMessage());
                }
            }
        }
    }
}
