package es.ua.sd.wm.central.infrastructure.socket;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.domain.Station;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** The Monitor connected for each station, so state changes can be pushed to it (STATE message). */
@Component
public class MonitorSessions implements StationChangePublisher {

    private final Map<String, MonitorSession> sessionsByStation = new ConcurrentHashMap<>();

    /** Makes this session the current one for the station and returns the previous one, if any. */
    MonitorSession register(String stationId, MonitorSession session) {
        return sessionsByStation.put(stationId, session);
    }

    /** Forgets the session only if it is still the current one. Returns true if it was. */
    boolean removeIfCurrent(String stationId, MonitorSession session) {
        return sessionsByStation.remove(stationId, session);
    }

    /** Used when WM_Central shuts down. */
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
