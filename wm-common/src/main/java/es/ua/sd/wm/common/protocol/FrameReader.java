package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Collects complete frames from a byte stream. The caller owns and closes the stream. */
public final class FrameReader {

    private static final int DEFAULT_MAXIMUM_MESSAGE_LENGTH = 4096;

    private final InputStream input;
    private final int maximumMessageLength;

    public FrameReader(InputStream input) {
        this(input, DEFAULT_MAXIMUM_MESSAGE_LENGTH);
    }

    public FrameReader(InputStream input, int maximumMessageLength) {
        Validator.requireNonNull(input, "Input stream");
        Validator.requirePositive(maximumMessageLength, "Maximum message length");

        this.input = input;
        this.maximumMessageLength = maximumMessageLength;
    }

    /** Returns the next complete frame, or null if the stream ends before a new frame starts. */
    public byte[] readFrame() throws IOException {
        int firstByte = input.read();
        if (firstByte == -1) {
            return null;
        }
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

        frame.write(nextByte);
        // Always read one checksum byte, even when its value happens to be STX or ETX.
        frame.write(readRequiredByte());

        return frame.toByteArray();
    }

    private int readRequiredByte() throws IOException {
        int nextByte = input.read();
        if (nextByte == -1) {
            throw new EOFException("Connection ended before the frame was complete");
        }

        return nextByte;
    }
}
