package es.ua.sd.wm.operator.application.port.out;

import es.ua.sd.wm.common.event.IrrigationRequest;

/** Entrega una petición de riego a WM_Central. */
public interface IrrigationRequestSender {

    void send(IrrigationRequest request);
}
