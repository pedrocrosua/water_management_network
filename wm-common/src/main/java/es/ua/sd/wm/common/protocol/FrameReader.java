package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Lee tramas completas de un flujo de bytes. Quien crea el flujo es quien lo cierra. */
public final class FrameReader {

    // Lo que devuelve read() cuando el otro extremo cierra la conexión.
    static final int END_OF_STREAM = -1;

    private final InputStream input;
    private final int maximumMessageLength;

    public FrameReader(InputStream input) {
        this(input, FrameCodec.MAXIMUM_MESSAGE_LENGTH);
    }

    public FrameReader(InputStream input, int maximumMessageLength) {
        Validator.requireNonNull(input, "Input stream");
        Validator.requirePositive(maximumMessageLength, "Maximum message length");

        this.input = input;
        this.maximumMessageLength = maximumMessageLength;
    }

    /** Devuelve la siguiente trama completa, o null si el flujo acaba antes de empezar otra. */
    public byte[] readFrame() throws IOException {
        int firstByte = input.read();
        // Conexión cerrada
        if (firstByte == END_OF_STREAM) {
            return null;
        }
        // Si no empieza por STX, el flujo está corrupto
        if (firstByte != FrameCodec.START_OF_TEXT) {
            throw new IOException("Frame must start with STX");
        }

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(firstByte);

        int messageLength = 0;
        int nextByte = readRequiredByte();
        while (nextByte != FrameCodec.END_OF_TEXT) {
            if (messageLength >= maximumMessageLength) {
                throw new IOException("Message exceeds the maximum length of " + maximumMessageLength + " bytes");
            }

            frame.write(nextByte);
            messageLength++;
            nextByte = readRequiredByte();
        }

        frame.write(FrameCodec.END_OF_TEXT);
        // El LRC se lee siempre, aunque su valor coincida con STX o ETX.
        frame.write(readRequiredByte());

        return frame.toByteArray();
    }

    private int readRequiredByte() throws IOException {
        int nextByte = input.read();
        if (nextByte == END_OF_STREAM) {
            throw new EOFException("Connection ended before the frame was complete");
        }

        return nextByte;
    }
}
