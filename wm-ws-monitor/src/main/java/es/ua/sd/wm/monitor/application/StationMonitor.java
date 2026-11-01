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
 * Mantiene la estación registrada en WM_Central: conecta, se autentica y cada segundo avisa de
 * que sigue viva. Si se pierde la conexión, lo vuelve a intentar.
 *
 * <p>También vigila al Engine: una respuesta KO o un Engine callado es un LEAK para WM_Central,
 * y una respuesta OK posterior lo resuelve. Solo se informa de los cambios.
 *
 * <p>Hilos: {@link #run()} va en el hilo principal; los avisos del Engine llegan por el hilo del
 * socket. Por eso {@code leaking} solo se toca dentro de métodos synchronized.
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

    /** Se ejecuta hasta que se llama a {@link #stop()} o WM_Central rechaza la estación. */
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

    /** Se llama siempre desde un método synchronized. */
    private void startLeak(String reason) {
        leaking = true;
        log.warn(">>> LEAK in station {}: {}", station.id(), reason);
        tellCentral("LEAK", central::reportLeak);
    }

    /** WM_Central pone como disponible la estación que se conecta: si sigue la fuga, se vuelve a avisar. */
    private synchronized void reportLeakAgainIfStillLeaking() {
        if (leaking) {
            tellCentral("LEAK", central::reportLeak);
        }
    }

    private void tellCentral(String what, CentralMessage message) {
        if (!central.isOpen()) {
            log.warn("WM_Central is not connected; {} not sent (a LEAK still active is sent again after reconnecting)", what);
            return;
        }
        try {
            message.send();
        } catch (IOException failed) {
            log.warn("Could not send {} to WM_Central: {}", what, failed.getMessage());
        }
    }

    private void showState(String state) {
        log.info(">>> Station {} is now {}: {}", station.id(), state, stateText(state));
    }

    /** El color y el texto que muestra el panel de WM_Central para cada estado. */
    static String stateText(String state) {
        return switch (state) {
            case "AVAILABLE" -> "VERDE - Disponible";
            case "IRRIGATING" -> "VERDE - Regando";
            case "LEAK" -> "ROJO - Fuga";
            case "OUT_OF_SERVICE" -> "NARANJA - Fuera de Servicio";
            case "DISCONNECTED" -> "GRIS - Desconectada";
            default -> state; // un estado nuevo se muestra tal cual
        };
    }

    /** Un mensaje para WM_Central cuyo envío puede fallar. */
    private interface CentralMessage {
        void send() throws IOException;
    }
}
