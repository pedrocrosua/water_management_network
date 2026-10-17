package es.ua.sd.wm.central.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.application.port.out.StationRepository;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.model.StationState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plain unit test: in-memory fakes instead of SQLite and the web panel. */
class StationRegistryTest {

    private final FakeRepository repository = new FakeRepository();
    private final List<Station> published = new ArrayList<>();
    private final StationChangePublisher publisher = published::add;

    @Test
    void onStartEveryStationIsShownAsDisconnected() {
        repository.save(new Station("WS-02", "Jardines", StationState.AVAILABLE, false));
        repository.save(new Station("WS-01", "Canalejas", StationState.IRRIGATING, false));

        StationRegistry registry = new StationRegistry(repository, publisher);

        assertThat(registry.findAll())
                .extracting(Station::id, Station::state)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("WS-01", StationState.DISCONNECTED),
                        org.assertj.core.groups.Tuple.tuple("WS-02", StationState.DISCONNECTED));
    }

    @Test
    void theDisconnectedStateIsAlsoSavedInTheDatabase() {
        repository.save(new Station("WS-01", "Canalejas", StationState.AVAILABLE, false));

        new StationRegistry(repository, publisher);

        assertThat(repository.stations.get("WS-01").state()).isEqualTo(StationState.DISCONNECTED);
    }

    @Test
    void outOfServiceOrderSurvivesARestart() {
        repository.save(new Station("WS-01", "Canalejas", StationState.OUT_OF_SERVICE, true));

        StationRegistry registry = new StationRegistry(repository, publisher);

        assertThat(registry.findAll().getFirst().outOfService()).isTrue();
    }

    @Test
    void everyLoadedStationIsAnnouncedToThePanel() {
        repository.save(new Station("WS-01", "Canalejas", StationState.AVAILABLE, false));

        new StationRegistry(repository, publisher);

        assertThat(published).extracting(Station::id).containsExactly("WS-01");
    }

    private static final class FakeRepository implements StationRepository {

        private final Map<String, Station> stations = new LinkedHashMap<>();

        @Override
        public List<Station> findAll() {
            return List.copyOf(stations.values());
        }

        @Override
        public void save(Station station) {
            stations.put(station.id(), station);
        }
    }
}
