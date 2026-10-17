package es.ua.sd.wm.operator.application;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.validation.Validator;
import es.ua.sd.wm.operator.application.port.out.IrrigationRequestSender;
import java.util.UUID;

/** Asks WM_Central for irrigations in the name of one operator. */
public class IrrigationRequests {

    private final String operatorId;
    private final IrrigationRequestSender sender;

    public IrrigationRequests(String operatorId, IrrigationRequestSender sender) {
        Validator.requireNonEmpty(operatorId, "Operator id (--operator.id)");
        this.operatorId = operatorId;
        this.sender = sender;
    }

    /** Sends the request and returns it; the answers arrive later as notifications. */
    public IrrigationRequest request(String stationId, int limitSeconds) {
        // Every request gets a unique id so its notifications can be matched with it.
        IrrigationRequest request = new IrrigationRequest(UUID.randomUUID().toString(),
                IrrigationSource.OPERATOR, operatorId, stationId, limitSeconds);
        sender.send(request);
        return request;
    }
}
