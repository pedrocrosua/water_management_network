package es.ua.sd.wm.engine.application.port.out;

import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;

/** Enlace con el WM_WS_M de la estación. La aplicación no sabe que es un socket. */
public interface MonitorConnection extends AutoCloseable {

    /**
     * Conecta, dice HELLO y espera la identidad de estación que asigna el Monitor. Desde entonces,
     * la conexión responde sola a los PING del Monitor.
     *
     * @throws IOException si no se puede llegar al Monitor o no responde
     */
    StationIdentity connect() throws IOException;

    boolean isOpen();

    @Override
    void close();
}
