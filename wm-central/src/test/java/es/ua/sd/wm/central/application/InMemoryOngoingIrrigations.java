package es.ua.sd.wm.central.application;

import es.ua.sd.wm.central.application.port.out.OngoingIrrigationRepository;
import es.ua.sd.wm.central.domain.OngoingIrrigation;
import java.util.ArrayList;
import java.util.List;

/** Riegos en curso para los tests: en lugar de SQLite, una lista. */
class InMemoryOngoingIrrigations implements OngoingIrrigationRepository {

    private final List<OngoingIrrigation> saved = new ArrayList<>();

    @Override
    public void save(OngoingIrrigation irrigation) {
        saved.add(irrigation);
    }

    @Override
    public void delete(String requestId) {
        saved.removeIf(irrigation -> irrigation.request().requestId().equals(requestId));
    }

    @Override
    public List<OngoingIrrigation> findAll() {
        return new ArrayList<>(saved);
    }
}
