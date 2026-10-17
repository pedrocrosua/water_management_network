package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.common.event.StationCommand;

/** Delivers an order to the Engine of one station. */
public interface StationCommandSender {

    void send(StationCommand command);
}
