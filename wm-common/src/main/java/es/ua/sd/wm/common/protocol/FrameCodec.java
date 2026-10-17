package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;

import java.nio.charset.StandardCharsets;

/** Encodes text messages into byte frames and decodes complete frames back into text. */
public final class FrameCodec {

    static final byte START_OF_TEXT = 0x02;
    static final byte END_OF_TEXT = 0x03;
    private static final int FRAME_OVERHEAD = 3;

    private FrameCodec() {
    }

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
        Validator.requireNonEmpty(message, "Message");
        Validator.requirePrintableAscii(message, "Message");

        return message;
    }

    private static byte calculateChecksum(byte[] frame) {
        byte checksum = 0;

        // LRC includes DATA and ETX, but excludes STX and the checksum itself.
        for (int index = 1; index < frame.length - 1; index++) {
            checksum ^= frame[index];
        }

        return checksum;
    }
}
