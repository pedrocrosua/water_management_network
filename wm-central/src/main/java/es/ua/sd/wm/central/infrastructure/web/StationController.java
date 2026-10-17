package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.central.domain.Station;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** HTTP endpoints used by the monitoring panel. */
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

    /** Current state of every station, once. */
    @GetMapping
    List<StationView> findAll() {
        return registry.findAll().stream().map(StationView::from).toList();
    }

    /** Opens a live stream: every station now, and then each change as it happens. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        SseEmitter panel = new SseEmitter(NEVER_TIME_OUT);
        // Register first, so no change is lost while the current state is being sent.
        broadcaster.addPanel(panel);
        for (Station station : registry.findAll()) {
            broadcaster.sendStation(panel, station);
        }
        return panel;
    }
}
