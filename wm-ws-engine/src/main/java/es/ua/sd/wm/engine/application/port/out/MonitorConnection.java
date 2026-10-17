package es.ua.sd.wm.engine.application.port.out;

import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;

/** Link with the station's WM_WS_M. The application does not know it is a socket. */
public interface MonitorConnection extends AutoCloseable {

    /**
     * Connects, says HELLO and waits for the station identity the Monitor assigns. From then on
     * the connection answers the Monitor's PINGs by itself.
     *
     * @throws IOException if the Monitor cannot be reached or does not answer
     */
    StationIdentity connect() throws IOException;

    boolean isOpen();

    @Override
    void close();
}
