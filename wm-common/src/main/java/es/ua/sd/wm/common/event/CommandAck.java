package es.ua.sd.wm.common.event;

import es.ua.sd.wm.common.validation.Validator;

/**
 * Respuesta del Engine a una {@link StationCommand}.
 *
 * @param command  orden a la que responde; no se llama "type" porque ese nombre ya lo usa
 *                 el JSON para distinguir los eventos (ver {@link StationEvent})
 * @param accepted true si la orden se ha cumplido
 * @param reason   motivo del rechazo; null si se aceptó
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
