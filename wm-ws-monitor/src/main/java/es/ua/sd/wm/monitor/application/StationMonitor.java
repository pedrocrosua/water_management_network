package es.ua.sd.wm.monitor.application;

import es.ua.sd.wm.monitor.application.port.in.EngineEvents;
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
 *
 * <p>It also watches the station's Engine: a fault answer or a silent Engine is a LEAK for
 * WM_Central, and a healthy answer after that clears it. Only changes are reported.
 */
public class StationMonitor implements EngineEvents {

    private static final Logger log = LoggerFactory.getLogger(StationMonitor.class);

    private final StationIdentity station;
    private final CentralConnection central;
    private final Duration healthInterval;
    private final Duration retryDelay;

    private volatile boolean running = true;
    private boolean leaking;

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

    @Override
    public synchronized void engineAnswered(boolean healthy) {
        if (healthy && leaking) {
            leaking = false;
            log.info(">>> Engine of station {} works again", station.id());
            tellCentral("LEAK_CLEARED", central::reportLeakCleared);
        } else if (!healthy && !leaking) {
            startLeak("the Engine reports a fault");
        }
    }

    @Override
    public synchronized void engineLost() {
        if (!leaking) {
            startLeak("the Engine stopped answering");
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
        reportLeakAgainIfStillLeaking();

        while (running && central.isOpen()) {
            central.sendHealth();
            Thread.sleep(healthInterval);
        }
        if (running) {
            throw new IOException("connection closed");
        }
    }

    private void startLeak(String reason) {
        leaking = true;
        log.warn(">>> LEAK in station {}: {}", station.id(), reason);
        tellCentral("LEAK", central::reportLeak);
    }

    /** WM_Central marks a station that connects as available, so an active leak is reported again. */
    private synchronized void reportLeakAgainIfStillLeaking() {
        if (leaking) {
            tellCentral("LEAK", central::reportLeak);
        }
    }

    private void tellCentral(String what, CentralMessage message) {
        if (!central.isOpen()) {
            log.warn("WM_Central is not connected; {} will be sent after reconnecting", what);
            return;
        }
        try {
            message.send();
        } catch (IOException failed) {
            log.warn("Could not send {} to WM_Central: {}", what, failed.getMessage());
        }
    }

    private void showState(String state) {
        log.info(">>> Station {} is now {}", station.id(), state);
    }

    /** One message for WM_Central that may fail to be sent. */
    private interface CentralMessage {
        void send() throws IOException;
    }
}
