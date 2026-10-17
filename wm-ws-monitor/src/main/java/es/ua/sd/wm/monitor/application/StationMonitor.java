package es.ua.sd.wm.monitor.application;

import es.ua.sd.wm.monitor.application.port.out.CentralConnection;
import es.ua.sd.wm.monitor.application.port.out.CentralConnection.AuthenticationRejectedException;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the station registered in WM_Central: connects, authenticates and then reports that
 * the station is alive every second. If the connection is lost it tries again.
 */
public class StationMonitor {

    private static final Logger log = LoggerFactory.getLogger(StationMonitor.class);

    private final StationIdentity station;
    private final CentralConnection central;
    private final Duration healthInterval;
    private final Duration retryDelay;

    private volatile boolean running = true;

    public StationMonitor(StationIdentity station, CentralConnection central,
                          Duration healthInterval, Duration retryDelay) {
        this.station = station;
        this.central = central;
        this.healthInterval = healthInterval;
        this.retryDelay = retryDelay;
    }

    /** Runs until {@link #stop()} is called or WM_Central rejects the station. */
    public void run() throws InterruptedException {
        while (running) {
            try {
                connectAndReportHealth();
            } catch (AuthenticationRejectedException rejected) {
                log.error("{}. Check --station.id and --station.location.", rejected.getMessage());
                return;
            } catch (IOException lost) {
                log.warn("No connection with WM_Central ({}). Retrying in {} s",
                        lost.getMessage(), retryDelay.toSeconds());
            } finally {
                central.close();
            }
            if (running) {
                Thread.sleep(retryDelay);
            }
        }
    }

    public void stop() {
        running = false;
        central.close();
    }

    private void connectAndReportHealth() throws IOException, InterruptedException {
        String state = central.connect(station, this::showState);
        log.info("{} authenticated in WM_Central", station.id());
        showState(state);

        while (running && central.isOpen()) {
            central.sendHealth();
            Thread.sleep(healthInterval);
        }
        if (running) {
            throw new IOException("connection closed");
        }
    }

    private void showState(String state) {
        log.info(">>> Station {} is now {}", station.id(), state);
    }
}
