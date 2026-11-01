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

/**
 * Guarda en memoria el estado actual de cada estación: es la única fuente de verdad.
 *
 * <p>Al arrancar carga las estaciones de la base de datos y las marca todas como
 * DISCONNECTED, porque WM_Central no sabe su estado real hasta que cada una se conecta.
 *
 * <p>Todo cambio pasa por {@link #change}, que actualiza una estación cada vez: dos cambios de
 * la misma estación nunca se mezclan, y estaciones distintas cambian en paralelo.
 */
public class StationRegistry {

    private final StationRepository repository;
    // A quién se avisa de cada cambio: el panel web, los Monitores conectados y Kafka.
    private final List<StationChangePublisher> changePublishers;
    private final Map<String, Station> stationsById = new ConcurrentHashMap<>();

    public StationRegistry(StationRepository repository, List<StationChangePublisher> changePublishers) {
        this.repository = repository;
        this.changePublishers = changePublishers;
        loadStationsAsDisconnected();
    }

    /** Al arrancar, cada estación queda desconectada: en memoria, en SQLite y para los avisados. */
    private void loadStationsAsDisconnected() {
        for (Station storedStation : repository.findAll()) {
            Station station = storedStation.disconnected();
            repository.save(station);
            stationsById.put(station.id(), station);
            changePublishers.forEach(publisher -> publisher.publish(station));
        }
    }

    public List<Station> findAll() {
        return stationsById.values().stream()
                .sorted(Comparator.comparing(Station::id))
                .toList();
    }

    public Optional<Station> find(String stationId) {
        return Optional.ofNullable(stationsById.get(stationId));
    }

    /** Un Monitor se ha autenticado. Una estación desconocida se registra con la ubicación que envía. */
    public Station connect(String stationId, String location) {
        return change(stationId, current -> {
            Station station = current != null ? current.locatedAt(location) : Station.register(stationId, location);
            return station.connected();
        });
    }

    /** El Monitor de la estación ya no está conectado. */
    public void disconnect(String stationId) {
        changeExisting(stationId, Station::disconnected);
    }

    /** El Engine abrió la válvula. Devuelve la estación, que solo pasa a regar si estaba disponible. */
    public Optional<Station> startIrrigation(String stationId) {
        return Optional.ofNullable(changeExisting(stationId, Station::irrigating));
    }

    /** El Engine cerró la válvula. */
    public void endIrrigation(String stationId) {
        changeExisting(stationId, Station::irrigationEnded);
    }

    /** El Monitor detectó una avería en su Engine. */
    public void reportLeak(String stationId) {
        changeExisting(stationId, Station::leaking);
    }

    /** El Monitor avisa de que la avería está reparada. */
    public void clearLeak(String stationId) {
        changeExisting(stationId, Station::leakCleared);
    }

    /** WM_Central bloquea la estación. El bloqueo se guarda y sigue tras reiniciar. */
    public void block(String stationId) {
        changeExisting(stationId, Station::blocked);
    }

    /** WM_Central vuelve a activar la estación. */
    public void activate(String stationId) {
        changeExisting(stationId, Station::activated);
    }

    /** Cambia una estación que ya existe. Si no existe, no hace nada y devuelve null. */
    private Station changeExisting(String stationId, UnaryOperator<Station> update) {
        return change(stationId, current -> current == null ? null : update.apply(current));
    }

    private Station change(String stationId, UnaryOperator<Station> update) {
        // compute() bloquea solo la entrada de esta estación mientras se actualiza y se guarda.
        // Si se devuelve null y la estación no existía, no se crea ninguna entrada.
        Station changed = stationsById.compute(stationId, (id, current) -> {
            Station updated = update.apply(current);
            if (updated != null && !updated.equals(current)) {
                repository.save(updated); // en SQLite solo se escribe si ha cambiado
            }
            return updated;
        });
        if (changed != null) {
            // Se avisa aunque no haya cambiado: repetir el estado no hace daño.
            changePublishers.forEach(publisher -> publisher.publish(changed));
        }
        return changed;
    }
}
