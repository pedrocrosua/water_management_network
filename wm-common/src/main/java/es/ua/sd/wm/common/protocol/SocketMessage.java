package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;
import java.util.Arrays;
import java.util.List;

/**
 * Mensaje de socket: un tipo seguido de sus campos, separados por '#'.
 * Por ejemplo {@code AUTH#WS-01#Parque de Canalejas}.
 */
public record SocketMessage(MessageType type, List<String> fields) {

    public static final String SEPARATOR = "#";

    public SocketMessage {
        Validator.requireNonNull(type, "Message type");
        Validator.requireNonNull(fields, "Fields");
        fields = List.copyOf(fields);
        for (String field : fields) {
            Validator.requirePrintableAscii(field, "Field");
            if (field.contains(SEPARATOR)) {
                throw new IllegalArgumentException("Field must not contain " + SEPARATOR + ": " + field);
            }
        }
    }

    public static SocketMessage of(MessageType type, String... fields) {
        return new SocketMessage(type, List.of(fields));
    }

    /** Lee un mensaje como "STATE#WS-01#AVAILABLE". Un tipo desconocido lanza excepción. */
    public static SocketMessage parse(String text) {
        Validator.requireNonEmpty(text, "Message");

        // -1 conserva los campos vacíos del final: "STATE#WS-01#" no se recorta sin avisar.
        String[] parts = text.split(SEPARATOR, -1);
        MessageType type;
        try {
            type = MessageType.valueOf(parts[0]);
        } catch (IllegalArgumentException unknownType) {
            throw new IllegalArgumentException("Unknown message type: " + parts[0]);
        }
        return new SocketMessage(type, Arrays.asList(parts).subList(1, parts.length));
    }

    /** El campo de esa posición (0 es el primero después del tipo). */
    public String field(int index) {
        if (index < 0 || index >= fields.size()) {
            throw new IllegalArgumentException(type + " message has no field number " + index);
        }
        return fields.get(index);
    }

    /** El texto que viaja dentro de la trama. */
    public String format() {
        if (fields.isEmpty()) {
            return type.name();
        }
        return type.name() + SEPARATOR + String.join(SEPARATOR, fields);
    }

    @Override
    public String toString() {
        return format();
    }
}
