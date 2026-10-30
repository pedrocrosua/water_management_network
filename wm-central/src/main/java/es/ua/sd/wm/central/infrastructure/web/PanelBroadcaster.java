package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.application.port.out.IrrigationProgressPublisher;
import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.Telemetry;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Envía a cada panel abierto en el navegador los cambios de las estaciones y la telemetría de
 * los riegos, con Server-Sent Events: una conexión HTTP que se queda abierta y por la que el
 * servidor sigue enviando datos.
 */
@Component
public class PanelBroadcaster implements StationChangePublisher, IrrigationProgressPublisher {

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

    /** Caudal, agua y tiempo de un riego, una vez por segundo. El panel lo muestra en la tarjeta. */
    @Override
    public void publish(Telemetry telemetry) {
        for (SseEmitter panel : openPanels) {
            send(panel, "telemetry", telemetry);
        }
    }

    public void sendStation(SseEmitter panel, Station station) {
        send(panel, "station", StationView.from(station));
    }

    private void send(SseEmitter panel, String eventName, Object data) {
        try {
            panel.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException closedPanel) {
            // Se cerró la pestaña del navegador: se olvida.
            openPanels.remove(panel);
        }
    }
}
