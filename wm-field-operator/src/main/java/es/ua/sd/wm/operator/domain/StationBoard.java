package es.ua.sd.wm.operator.domain;

import es.ua.sd.wm.common.event.StationStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The latest known state of every station, as WM_Central publishes it. */
public class StationBoard {

    private final Map<String, StationStatus> stationsById = new ConcurrentHashMap<>();

    public void update(StationStatus status) {
        stationsById.put(status.stationId(), status);
    }

    public List<StationStatus> all() {
        return stationsById.values().stream()
                .sorted(Comparator.comparing(StationStatus::stationId))
                .toList();
    }
}
