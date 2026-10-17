package es.ua.sd.wm.operator.application.port.out;

import es.ua.sd.wm.common.event.IrrigationRequest;

/** Delivers an irrigation request to WM_Central. */
public interface IrrigationRequestSender {

    void send(IrrigationRequest request);
}
