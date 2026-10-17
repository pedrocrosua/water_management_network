package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.domain.Station;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes station changes to every open panel in the browser, using Server-Sent Events:
 * an HTTP connection that stays open and through which the server keeps sending data.
 */
@Component
public class PanelBroadcaster implements StationChangePublisher {

    private final List<SseEmitter> openPanels = new CopyOnWriteArrayList<>();

    public void addPanel(SseEmitter panel) {
        openPanels.add(panel);
        panel.onCompletion(() -> openPanels.remove(panel));
        panel.onTimeout(() -> openPanels.remove(panel));
        panel.onError(error -> openPanels.remove(panel));
    }

    @Override
    public void publish(Station station) {
        for (SseEmitter panel : openPanels) {
            sendStation(panel, station);
        }
    }

    public void sendStation(SseEmitter panel, Station station) {
        try {
            panel.send(SseEmitter.event().name("station").data(StationView.from(station)));
        } catch (IOException | IllegalStateException closedPanel) {
            // The browser tab was closed: forget it.
            openPanels.remove(panel);
        }
    }
}
