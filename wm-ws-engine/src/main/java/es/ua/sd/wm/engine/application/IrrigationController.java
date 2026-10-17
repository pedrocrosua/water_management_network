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
 * Runs the station's irrigations: carries out WM_Central's orders, reports the flow every
 * second and closes the valve when the time limit is reached or someone stops it.
 *
 * <p>Every public method is synchronized: an order, the clock tick and the menu never change
 * the valve at the same time.
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
            default -> log.warn("{} is not supported yet", command.type());
        }
    }

    /** Called once per second. While irrigating, reports the flow and checks the time limit. */
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

    /** Stop option of the station menu. Returns false if there was nothing to stop. */
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
            log.info(">>> Valve OPEN: irrigating for operator {} (limit {} s)",
                    command.operatorId(), command.limitSeconds());
            accept(command);
        }
    }

    /** WM_Central stops the irrigation; without a reason it counts as a manual stop. */
    private void stopByOrder(StationCommand command) {
        if (current != null) {
            end(command.reason() != null ? command.reason() : EndReason.MANUAL_STOP);
        } else {
            valve.close();
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
