package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.common.event.Telemetry;

/** Shows how an irrigation is going (flow, water, time) while it lasts. Nothing is stored. */
public interface IrrigationProgressPublisher {

    void publish(Telemetry telemetry);
}
