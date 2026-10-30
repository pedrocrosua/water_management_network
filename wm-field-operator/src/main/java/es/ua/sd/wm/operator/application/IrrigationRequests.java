package es.ua.sd.wm.operator.application;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.validation.Validator;
import es.ua.sd.wm.operator.application.port.out.IrrigationRequestSender;
import java.util.UUID;

/** Pide riegos a WM_Central en nombre de un operario. */
public class IrrigationRequests {

    private final String operatorId;
    private final IrrigationRequestSender sender;

    public IrrigationRequests(String operatorId, IrrigationRequestSender sender) {
        Validator.requireNonEmpty(operatorId, "Operator id (--operator.id)");
        this.operatorId = operatorId;
        this.sender = sender;
    }

    /** Envía la petición y la devuelve; las respuestas llegan después como notificaciones. */
    public IrrigationRequest request(String stationId, int limitSeconds) {
        // Cada petición lleva un id único para poder relacionarla con sus notificaciones.
        IrrigationRequest request = new IrrigationRequest(UUID.randomUUID().toString(),
                IrrigationSource.OPERATOR, operatorId, stationId, limitSeconds);
        sender.send(request);
        return request;
    }
}
