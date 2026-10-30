package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;

import java.nio.charset.StandardCharsets;

/** Convierte un mensaje de texto en trama (STX + DATA + ETX + LRC) y al revés. */
public final class FrameCodec {

    // Bytes de control del protocolo.
    static final byte START_OF_TEXT = 0x02; // STX: empieza la trama
    static final byte END_OF_TEXT = 0x03;   // ETX: acaba el mensaje
    static final byte ACK = 0x06;           // trama recibida correctamente
    static final byte NACK = 0x15;          // trama dañada: hay que reenviarla

    // Longitud máxima del mensaje (DATA), en bytes.
    static final int MAXIMUM_MESSAGE_LENGTH = 4096;
    // Bytes que la trama añade al mensaje: STX + ETX + LRC.
    private static final int FRAME_OVERHEAD = 3;

    private FrameCodec() {
    }

    /** Construye la trama de un mensaje ASCII imprimible y no vacío. */
    public static byte[] encode(String message) {
        Validator.requireNonEmpty(message, "Message");
        Validator.requirePrintableAscii(message, "Message");

        byte[] messageBytes = message.getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[messageBytes.length + FRAME_OVERHEAD];

        frame[0] = START_OF_TEXT;
        System.arraycopy(messageBytes, 0, frame, 1, messageBytes.length);
        frame[frame.length - 2] = END_OF_TEXT;
        frame[frame.length - 1] = calculateChecksum(frame);

        return frame;
    }

    /** Comprueba STX, ETX y LRC, y devuelve el mensaje de la trama. */
    public static String decode(byte[] frame) {
        Validator.requireNonNull(frame, "Frame");

        if (frame.length < FRAME_OVERHEAD + 1) {
            throw new IllegalArgumentException("Frame must contain a non-empty message, STX, ETX and LRC");
        }
        if (frame[0] != START_OF_TEXT) {
            throw new IllegalArgumentException("Frame must start with STX");
        }
        if (frame[frame.length - 2] != END_OF_TEXT) {
            throw new IllegalArgumentException("Frame must end with ETX followed by LRC");
        }
        if (frame[frame.length - 1] != calculateChecksum(frame)) {
            throw new IllegalArgumentException("Frame checksum does not match its contents");
        }

        int messageLength = frame.length - FRAME_OVERHEAD;
        String message = new String(frame, 1, messageLength, StandardCharsets.US_ASCII);
        Validator.requirePrintableAscii(message, "Message");

        return message;
    }

    /** LRC: XOR de los bytes de DATA y del ETX (sin STX ni el propio LRC). */
    private static byte calculateChecksum(byte[] frame) {
        byte checksum = 0;
        for (int index = 1; index < frame.length - 1; index++) {
            checksum ^= frame[index];
        }
        return checksum;
    }
}
