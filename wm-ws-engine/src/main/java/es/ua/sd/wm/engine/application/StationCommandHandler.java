package es.ua.sd.wm.engine.application;

import es.ua.sd.wm.common.event.CommandAck;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.engine.application.port.out.StationEventPublisher;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Carries out the orders WM_Central sends to this station and answers each one. */
public class StationCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StationCommandHandler.class);

    private final Valve valve;
    private final LeakSensor sensor;
    private final StationEventPublisher publisher;

    public StationCommandHandler(Valve valve, LeakSensor sensor, StationEventPublisher publisher) {
        this.valve = valve;
        this.sensor = sensor;
        this.publisher = publisher;
    }

    /** Synchronized: two orders never change the valve at the same time. */
    public synchronized void execute(StationCommand command) {
        log.info("Order received: {} (request {})", command.type(), command.requestId());
        switch (command.type()) {
            case START -> start(command);
            case STOP -> stop(command);
            default -> log.warn("{} is not supported yet", command.type());
        }
    }

    private void start(StationCommand command) {
        if (sensor.isLeaking()) {
            reject(command, "The station has a leak");
        } else if (valve.isOpen()) {
            reject(command, "The station is already irrigating");
        } else {
            valve.open();
            log.info(">>> Valve OPEN: irrigating for operator {} (limit {} s)",
                    command.operatorId(), command.limitSeconds());
            accept(command);
        }
    }

    private void stop(StationCommand command) {
        valve.close();
        log.info(">>> Valve CLOSED");
        accept(command);
    }

    private void accept(StationCommand command) {
        publisher.publish(new CommandAck(command.requestId(), command.stationId(), command.type(), true, null));
    }

    private void reject(StationCommand command, String reason) {
        log.warn("{} rejected: {}", command.type(), reason);
        publisher.publish(new CommandAck(command.requestId(), command.stationId(), command.type(), false, reason));
    }
}
