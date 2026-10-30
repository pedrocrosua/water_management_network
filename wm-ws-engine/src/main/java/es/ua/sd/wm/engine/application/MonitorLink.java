package es.ua.sd.wm.engine.application;

import es.ua.sd.wm.engine.application.port.out.MonitorConnection;
import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mantiene el Engine conectado a su Monitor. Si se pierde la conexión, lo vuelve a intentar. */
public class MonitorLink {

    private static final Logger log = LoggerFactory.getLogger(MonitorLink.class);

    private final MonitorConnection monitor;
    private final Consumer<StationIdentity> onIdentified;
    private final Duration retryDelay;
    private final Duration checkInterval;

    private volatile boolean running = true;

    /** onIdentified se ejecuta cada vez que un Monitor le dice al Engine a qué estación pertenece. */
    public MonitorLink(MonitorConnection monitor, Consumer<StationIdentity> onIdentified,
                       Duration retryDelay, Duration checkInterval) {
        this.monitor = monitor;
        this.onIdentified = onIdentified;
        this.retryDelay = retryDelay;
        this.checkInterval = checkInterval;
    }

    /** Se ejecuta hasta que se llama a {@link #stop()}. */
    public void run() throws InterruptedException {
        while (running) {
            try {
                stayConnected();
            } catch (IOException lost) {
                log.warn("No connection with WM_WS_M ({}). Retrying in {} s",
                        lost.getMessage(), retryDelay.toSeconds());
            } finally {
                monitor.close();
            }
            if (running) {
                Thread.sleep(retryDelay);
            }
        }
    }

    public void stop() {
        running = false;
        monitor.close();
    }

    private void stayConnected() throws IOException, InterruptedException {
        StationIdentity station = monitor.connect();
        log.info(">>> Working as Engine of station {} ({})", station.id(), station.location());
        onIdentified.accept(station);

        // La conexión responde sola a los PING; aquí solo se detecta cuándo se cierra.
        while (running && monitor.isOpen()) {
            Thread.sleep(checkInterval);
        }
        if (running) {
            throw new IOException("connection closed");
        }
    }
}
