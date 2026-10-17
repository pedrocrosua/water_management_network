package es.ua.sd.wm.monitor.infrastructure.engine;

import es.ua.sd.wm.monitor.application.port.in.EngineEvents;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for the station's WM_WS_E to connect. A station has one Engine: when a new one says
 * HELLO it replaces the previous connection (for example, after the Engine was restarted).
 */
public class EngineSocketServer {

    private static final Logger log = LoggerFactory.getLogger(EngineSocketServer.class);

    private final int port;
    private final StationIdentity station;
    private final EngineEvents events;
    private final Duration pingInterval;

    private ServerSocket serverSocket;
    private EngineSession currentEngine;
    private volatile boolean running;

    public EngineSocketServer(int port, StationIdentity station, EngineEvents events, Duration pingInterval) {
        this.port = port;
        this.station = station;
        this.events = events;
        this.pingInterval = pingInterval;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread.ofPlatform().name("engine-accept").start(this::acceptEngines);
        log.info("Waiting for the Engine on port {}", getPort());
    }

    /** The real port; useful when started with port 0 (any free port), as the tests do. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // We are shutting down anyway.
        }
        EngineSession engine = replaceCurrentEngine(null);
        if (engine != null) {
            engine.close();
        }
    }

    /** Called by a session after HELLO. Returns the session it replaces, if any. */
    synchronized EngineSession replaceCurrentEngine(EngineSession newEngine) {
        EngineSession previous = currentEngine;
        currentEngine = newEngine;
        return previous;
    }

    /** True only for the session that is still the station's Engine; it is then forgotten. */
    synchronized boolean removeIfCurrent(EngineSession engine) {
        if (currentEngine != engine) {
            return false;
        }
        currentEngine = null;
        return true;
    }

    private void acceptEngines() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                new EngineSession(socket, station, events, this, pingInterval);
            } catch (IOException failed) {
                if (running) {
                    log.warn("Could not accept an Engine connection: {}", failed.getMessage());
                }
            }
        }
    }
}
