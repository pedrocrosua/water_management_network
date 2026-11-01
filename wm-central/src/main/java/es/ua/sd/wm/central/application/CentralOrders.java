package es.ua.sd.wm.central.application;

import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.model.StationState;
import java.util.UUID;

/**
 * Órdenes que se lanzan desde el panel de WM_Central, a una estación o a todas:
 * regar (CU-11), bloquear y activar (CU-10).
 */
public class CentralOrders {

    private final StationRegistry registry;
    private final IrrigationService irrigations;

    public CentralOrders(StationRegistry registry, IrrigationService irrigations) {
        this.registry = registry;
        this.irrigations = irrigations;
    }

    /** CU-11: pide un riego igual que un operario, pero con origen CENTRAL y sin operario. */
    public void irrigate(String stationId, int seconds) {
        requireKnownStation(stationId);
        String requestId = UUID.randomUUID().toString();
        irrigations.request(new IrrigationRequest(requestId, IrrigationSource.CENTRAL, null, stationId, seconds));
    }

    /** CU-11 en todas: igual que pulsar "Regar" en cada estación disponible. Devuelve a cuántas se ha pedido. */
    public int irrigateAll(int seconds) {
        int asked = 0;
        for (Station station : registry.findAll()) {
            if (station.state() == StationState.AVAILABLE) {
                irrigate(station.id(), seconds);
                asked++;
            }
        }
        return asked;
    }

    /** CU-10: la estación pasa a "Fuera de Servicio" y, si estaba regando, el riego se corta. */
    public void block(String stationId) {
        requireKnownStation(stationId);
        irrigations.stationBlocked(stationId);
    }

    /** Bloquea todas las estaciones. Devuelve cuántas son. */
    public int blockAll() {
        int blocked = 0;
        for (Station station : registry.findAll()) {
            block(station.id());
            blocked++;
        }
        return blocked;
    }

    /** CU-10: la estación vuelve a estar activa (verde). */
    public void activate(String stationId) {
        requireKnownStation(stationId);
        registry.activate(stationId);
    }

    /** Activa todas las estaciones. Devuelve cuántas son. */
    public int activateAll() {
        int activated = 0;
        for (Station station : registry.findAll()) {
            activate(station.id());
            activated++;
        }
        return activated;
    }

    private void requireKnownStation(String stationId) {
        if (registry.find(stationId).isEmpty()) {
            throw new IllegalArgumentException("Unknown station " + stationId);
        }
    }
}
