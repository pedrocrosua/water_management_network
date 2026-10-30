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
 * Espera a que se conecte el WM_WS_E de la estación. Una estación tiene un solo Engine: si uno
 * nuevo dice HELLO, sustituye a la conexión anterior (por ejemplo, tras reiniciar el Engine).
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

    /** El puerto real; útil al arrancar con el puerto 0 (cualquiera libre), como hacen los tests. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Se está apagando de todos modos.
        }
        EngineSession engine = replaceCurrentEngine(null); // quita la sesión actual para cerrarla
        if (engine != null) {
            engine.close();
        }
    }

    /** Lo llama una sesión tras el HELLO. Devuelve la sesión a la que sustituye, si había una. */
    synchronized EngineSession replaceCurrentEngine(EngineSession newEngine) {
        EngineSession previous = currentEngine;
        currentEngine = newEngine;
        return previous;
    }

    /** True solo para la sesión que sigue siendo el Engine de la estación, que entonces se olvida. */
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
                // No se guarda: la sesión vive en los hilos de su FrameChannel y, tras el HELLO,
                // queda como currentEngine.
                new EngineSession(socket, station, events, this, pingInterval);
            } catch (IOException failed) {
                if (running) {
                    log.warn("Could not accept an Engine connection: {}", failed.getMessage());
                }
            }
        }
    }
}
