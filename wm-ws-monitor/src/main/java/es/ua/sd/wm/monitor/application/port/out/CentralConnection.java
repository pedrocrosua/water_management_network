package es.ua.sd.wm.monitor.application.port.out;

import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.util.function.Consumer;

/** Enlace con WM_Central. La aplicación no sabe que es un socket. */
public interface CentralConnection extends AutoCloseable {

    /**
     * Conecta y autentica la estación.
     *
     * @param stateListener recibe cada estado que WM_Central envíe después (mensajes STATE)
     * @return el estado que WM_Central dio a la estación al aceptarla
     * @throws AuthenticationRejectedException si WM_Central no acepta la estación
     * @throws IOException si no se puede llegar a WM_Central
     */
    String connect(StationIdentity station, Consumer<String> stateListener) throws IOException;

    /** Avisa a WM_Central de que la estación sigue viva. */
    void sendHealth() throws IOException;

    /** Avisa a WM_Central de una avería (LEAK). */
    void reportLeak() throws IOException;

    /** Avisa a WM_Central de que la avería está reparada (LEAK_CLEARED). */
    void reportLeakCleared() throws IOException;

    boolean isOpen();

    @Override
    void close();

    /** WM_Central respondió AUTH_KO: reintentar con los mismos datos no sirve. */
    final class AuthenticationRejectedException extends IOException {

        public AuthenticationRejectedException(String reason) {
            super("WM_Central rejected the station: " + reason);
        }
    }
}
