package es.ua.sd.wm.central.infrastructure.socket;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.domain.Station;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** El Monitor conectado de cada estación, para enviarle los cambios de estado (mensaje STATE). */
@Component
public class MonitorSessions implements StationChangePublisher {

    private final Map<String, MonitorSession> sessionsByStation = new ConcurrentHashMap<>();

    /** Hace actual esta sesión de la estación y devuelve la anterior, si la había. */
    MonitorSession register(String stationId, MonitorSession session) {
        return sessionsByStation.put(stationId, session);
    }

    /** Olvida la sesión solo si sigue siendo la actual. Devuelve true si lo era. */
    boolean removeIfCurrent(String stationId, MonitorSession session) {
        return sessionsByStation.remove(stationId, session);
    }

    /** Se usa al apagar WM_Central. */
    void closeAll() {
        sessionsByStation.values().forEach(MonitorSession::close);
    }

    @Override
    public void publish(Station station) {
        MonitorSession session = sessionsByStation.get(station.id());
        if (session != null) {
            session.sendState(station);
        }
    }
}
