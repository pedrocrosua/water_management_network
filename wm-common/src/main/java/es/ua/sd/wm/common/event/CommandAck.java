package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.validation.Validator;

/**
 * The Engine's answer to a {@link StationCommand}.
 *
 * @param accepted true if the order was carried out
 * @param reason   why it was rejected; null when accepted
 */
public record CommandAck(
        String requestId,
        String stationId,
        CommandType command,
        boolean accepted,
        String reason) implements StationEvent {

    public CommandAck {
        Validator.requireNonEmpty(requestId, "Request id");
        Validator.requireNonEmpty(stationId, "Station id");
        Validator.requireNonNull(command, "Command type");
    }
}
