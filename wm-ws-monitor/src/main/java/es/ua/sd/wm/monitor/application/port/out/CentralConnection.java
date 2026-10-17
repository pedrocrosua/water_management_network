package es.ua.sd.wm.monitor.application.port.out;

import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.util.function.Consumer;

/** Link with WM_Central. The application does not know it is a socket. */
public interface CentralConnection extends AutoCloseable {

    /**
     * Connects and authenticates the station.
     *
     * @param stateListener receives every state WM_Central reports later (STATE messages)
     * @return the state WM_Central gave the station when accepting it
     * @throws AuthenticationRejectedException if WM_Central does not accept the station
     * @throws IOException if WM_Central cannot be reached
     */
    String connect(StationIdentity station, Consumer<String> stateListener) throws IOException;

    /** Tells WM_Central the station is alive. */
    void sendHealth() throws IOException;

    /** Tells WM_Central the station has a fault (LEAK). */
    void reportLeak() throws IOException;

    /** Tells WM_Central the fault is fixed (LEAK_CLEARED). */
    void reportLeakCleared() throws IOException;

    boolean isOpen();

    @Override
    void close();

    /** WM_Central answered AUTH_KO: retrying with the same data will not help. */
    final class AuthenticationRejectedException extends IOException {

        public AuthenticationRejectedException(String reason) {
            super("WM_Central rejected the station: " + reason);
        }
    }
}
