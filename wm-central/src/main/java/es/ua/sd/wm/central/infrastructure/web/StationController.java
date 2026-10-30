package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.central.domain.Station;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Endpoints HTTP que usa el panel de monitorización. */
@RestController
@RequestMapping("/api/stations")
class StationController {

    private static final long NEVER_TIME_OUT = 0L;

    private final StationRegistry registry;
    private final PanelBroadcaster broadcaster;

    StationController(StationRegistry registry, PanelBroadcaster broadcaster) {
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    /** Estado actual de todas las estaciones, una vez. */
    @GetMapping
    List<StationView> findAll() {
        return registry.findAll().stream().map(StationView::from).toList();
    }

    /** Abre un flujo en directo: primero todas las estaciones y después cada cambio según ocurre. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        SseEmitter panel = new SseEmitter(NEVER_TIME_OUT);
        // Primero se registra, para no perder ningún cambio mientras se envía el estado actual.
        broadcaster.addPanel(panel);
        for (Station station : registry.findAll()) {
            broadcaster.sendStation(panel, station);
        }
        return panel;
    }
}
