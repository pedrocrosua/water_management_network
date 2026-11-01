package es.ua.sd.wm.engine.application.port.out;

import es.ua.sd.wm.common.event.IrrigationRequest;

/** Envía a WM_Central un riego pedido desde el menú de la estación. */
public interface IrrigationRequestSender {

    void send(IrrigationRequest request);
}
