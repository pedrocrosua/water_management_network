package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;
import java.util.Arrays;
import java.util.List;

/**
 * A socket message: a type followed by its fields, separated by '#'.
 * For example {@code AUTH#WS-01#Parque de Canalejas}.
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

    /** Reads a message such as "STATE#WS-01#AVAILABLE". */
    public static SocketMessage parse(String text) {
        Validator.requireNonEmpty(text, "Message");

        // -1 keeps empty fields at the end, so "STATE#WS-01#" is not silently shortened.
        String[] parts = text.split(SEPARATOR, -1);
        MessageType type;
        try {
            type = MessageType.valueOf(parts[0]);
        } catch (IllegalArgumentException unknownType) {
            throw new IllegalArgumentException("Unknown message type: " + parts[0]);
        }
        return new SocketMessage(type, Arrays.asList(parts).subList(1, parts.length));
    }

    /** The field at that position (0 is the first field after the type). */
    public String field(int index) {
        if (index < 0 || index >= fields.size()) {
            throw new IllegalArgumentException(type + " message has no field number " + index);
        }
        return fields.get(index);
    }

    /** The text that travels inside the frame. */
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
