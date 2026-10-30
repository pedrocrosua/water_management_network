package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.common.event.StationCommand;

/** Entrega una orden al Engine de una estación. */
public interface StationCommandSender {

    void send(StationCommand command);
}
