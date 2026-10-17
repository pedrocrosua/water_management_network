package es.ua.sd.wm.central.application;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.application.port.out.StationRepository;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.model.StationState;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Keeps the current state of every station in memory: the single source of truth.
 *
 * <p>On start-up it loads the stations from the database and marks all of them as
 * DISCONNECTED, because WM_Central cannot know their real state until each one connects.
 */
@Service
public class StationRegistry {

    private final StationRepository repository;
    private final StationChangePublisher changePublisher;
    private final Map<String, Station> stationsById = new ConcurrentHashMap<>();

    public StationRegistry(StationRepository repository, StationChangePublisher changePublisher) {
        this.repository = repository;
        this.changePublisher = changePublisher;
        loadStationsAsDisconnected();
    }

    public List<Station> findAll() {
        return stationsById.values().stream()
                .sorted(Comparator.comparing(Station::id))
                .toList();
    }

    private void loadStationsAsDisconnected() {
        for (Station storedStation : repository.findAll()) {
            Station station = storedStation.withState(StationState.DISCONNECTED);
            repository.save(station);
            stationsById.put(station.id(), station);
            changePublisher.publish(station);
        }
    }
}
