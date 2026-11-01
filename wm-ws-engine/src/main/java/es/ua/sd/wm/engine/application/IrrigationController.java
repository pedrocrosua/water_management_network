package es.ua.sd.wm.engine.application;

import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.IrrigationEnded;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.engine.application.port.out.FlowMeter;
import es.ua.sd.wm.engine.application.port.out.StationEventPublisher;
import es.ua.sd.wm.engine.domain.Irrigation;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta los riegos de la estación: cumple las órdenes de WM_Central, informa del caudal cada
 * segundo y cierra la válvula al llegar al tiempo límite o cuando alguien lo para.
 *
 * <p>Todos los métodos públicos son synchronized: una orden, el reloj y el menú nunca cambian
 * la válvula a la vez.
 */
public class IrrigationController {

    private static final Logger log = LoggerFactory.getLogger(IrrigationController.class);

    private final Valve valve;
    private final LeakSensor sensor;
    private final FlowMeter flowMeter;
    private final StationEventPublisher publisher;

    private Irrigation current;

    public IrrigationController(Valve valve, LeakSensor sensor, FlowMeter flowMeter, StationEventPublisher publisher) {
        this.valve = valve;
        this.sensor = sensor;
        this.flowMeter = flowMeter;
        this.publisher = publisher;
    }

    public synchronized void execute(StationCommand command) {
        log.info("Order received: {} (request {})", command.type(), command.requestId());
        switch (command.type()) {
            case START -> start(command);
            case STOP -> stopByOrder(command);
            case DENIED -> log.warn(">>> WM_Central did not authorize the manual irrigation (request {})",
                    command.requestId());
            // WM_Central no envía otras órdenes al Engine.
            default -> log.warn("{} is not supported", command.type());
        }
    }

    public synchronized boolean isIrrigating() {
        return current != null;
    }

    /** Se llama una vez por segundo. Durante el riego, informa del caudal y comprueba el tiempo límite. */
    public synchronized void tick() {
        if (current == null) {
            return;
        }
        double flow = flowMeter.readLitresPerMinute();
        current.addSecond(flow);
        publisher.publish(new Telemetry(current.requestId(), current.stationId(), current.operatorId(),
                flow, current.litres(), current.elapsedSeconds()));
        log.info(">>> Irrigating: {} L/min, {} L in {} s",
                String.format("%.1f", flow), String.format("%.1f", current.litres()), current.elapsedSeconds());

        if (current.limitReached()) {
            end(EndReason.TIME_LIMIT);
        }
    }

    /** Opción de parar del menú de la estación. Devuelve false si no había nada que parar. */
    public synchronized boolean stopFromStation() {
        if (current == null) {
            return false;
        }
        end(EndReason.MANUAL_STOP);
        return true;
    }

    private void start(StationCommand command) {
        if (sensor.isLeaking()) {
            reject(command, "The station has a leak");
        } else if (current != null) {
            reject(command, "The station is already irrigating");
        } else {
            valve.open();
            current = new Irrigation(command.requestId(), command.stationId(), command.operatorId(),
                    command.limitSeconds());
            // Los riegos pedidos desde WM_Central no tienen operario.
            String operator = command.operatorId() != null ? command.operatorId() : "none";
            log.info(">>> Valve OPEN: irrigating for operator {} (limit {} s)", operator, command.limitSeconds());
            accept(command);
        }
    }

    /** WM_Central para el riego; sin motivo, cuenta como parada manual. */
    private void stopByOrder(StationCommand command) {
        if (current != null) {
            end(command.reason() != null ? command.reason() : EndReason.MANUAL_STOP);
        } else {
            valve.close(); // no había riego: se asegura de que la válvula queda cerrada
        }
        accept(command);
    }

    private void end(EndReason reason) {
        valve.close();
        publisher.publish(new IrrigationEnded(current.requestId(), current.stationId(), current.operatorId(),
                current.litres(), current.elapsedSeconds(), reason));
        log.info(">>> Valve CLOSED ({}): {} L in {} s",
                reason, String.format("%.1f", current.litres()), current.elapsedSeconds());
        current = null;
    }

    private void accept(StationCommand command) {
        publisher.publish(new CommandAck(command.requestId(), command.stationId(), command.type(), true, null));
    }

    private void reject(StationCommand command, String reason) {
        log.warn("{} rejected: {}", command.type(), reason);
        publisher.publish(new CommandAck(command.requestId(), command.stationId(), command.type(), false, reason));
    }
}
