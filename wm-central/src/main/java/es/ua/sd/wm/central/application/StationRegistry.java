package es.ua.sd.wm.central.application;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.application.port.out.StationRepository;
import es.ua.sd.wm.central.domain.Station;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

/**
 * Keeps the current state of every station in memory: the single source of truth.
 *
 * <p>On start-up it loads the stations from the database and marks all of them as
 * DISCONNECTED, because WM_Central cannot know their real state until each one connects.
 *
 * <p>Every change goes through {@link #change}, which updates one station at a time: two
 * changes to the same station never mix, while different stations change in parallel.
 */
@Service
public class StationRegistry {

    private final StationRepository repository;
    private final List<StationChangePublisher> changePublishers;
    private final Map<String, Station> stationsById = new ConcurrentHashMap<>();

    public StationRegistry(StationRepository repository, List<StationChangePublisher> changePublishers) {
        this.repository = repository;
        this.changePublishers = changePublishers;
        loadStationsAsDisconnected();
    }

    public List<Station> findAll() {
        return stationsById.values().stream()
                .sorted(Comparator.comparing(Station::id))
                .toList();
    }

    public Optional<Station> find(String stationId) {
        return Optional.ofNullable(stationsById.get(stationId));
    }

    /** A Monitor authenticated. Unknown stations are registered with the location they report. */
    public Station connect(String stationId, String location) {
        return change(stationId, current -> {
            Station station = current != null ? current.locatedAt(location) : Station.register(stationId, location);
            return station.connected();
        });
    }

    /** The station's Monitor is no longer connected. */
    public void disconnect(String stationId) {
        change(stationId, current -> current == null ? null : current.disconnected());
    }

    /** The station's Monitor detected a fault in its Engine. */
    public void reportLeak(String stationId) {
        change(stationId, current -> current == null ? null : current.leaking());
    }

    /** The station's Monitor reports the fault is fixed. */
    public void clearLeak(String stationId) {
        change(stationId, current -> current == null ? null : current.leakCleared());
    }

    private Station change(String stationId, UnaryOperator<Station> update) {
        // compute() locks only this station's entry while the update and the save run.
        Station changed = stationsById.compute(stationId, (id, current) -> {
            Station updated = update.apply(current);
            if (updated != null && !updated.equals(current)) {
                repository.save(updated);
            }
            return updated;
        });
        if (changed != null) {
            changePublishers.forEach(publisher -> publisher.publish(changed));
        }
        return changed;
    }

    private void loadStationsAsDisconnected() {
        for (Station storedStation : repository.findAll()) {
            Station station = storedStation.disconnected();
            repository.save(station);
            stationsById.put(station.id(), station);
            changePublishers.forEach(publisher -> publisher.publish(station));
        }
    }
}
