package es.ua.sd.wm.engine.application;

import es.ua.sd.wm.engine.application.port.out.MonitorConnection;
import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps the Engine connected to its Monitor. If the connection is lost it tries again. */
public class MonitorLink {

    private static final Logger log = LoggerFactory.getLogger(MonitorLink.class);

    private final MonitorConnection monitor;
    private final Consumer<StationIdentity> onIdentified;
    private final Duration retryDelay;
    private final Duration checkInterval;

    private volatile boolean running = true;
    private volatile StationIdentity currentStation;

    /** onIdentified runs every time a Monitor tells the Engine which station it belongs to. */
    public MonitorLink(MonitorConnection monitor, Consumer<StationIdentity> onIdentified,
                       Duration retryDelay, Duration checkInterval) {
        this.monitor = monitor;
        this.onIdentified = onIdentified;
        this.retryDelay = retryDelay;
        this.checkInterval = checkInterval;
    }

    /** Runs until {@link #stop()} is called. */
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

    /** The station assigned by the last Monitor that accepted this Engine, if any. */
    public Optional<StationIdentity> currentStation() {
        return Optional.ofNullable(currentStation);
    }

    private void stayConnected() throws IOException, InterruptedException {
        currentStation = monitor.connect();
        log.info(">>> Working as Engine of station {} ({})", currentStation.id(), currentStation.location());
        onIdentified.accept(currentStation);

        // The connection answers the PINGs by itself; here we only notice when it ends.
        while (running && monitor.isOpen()) {
            Thread.sleep(checkInterval);
        }
        if (running) {
            throw new IOException("connection closed");
        }
    }
}
