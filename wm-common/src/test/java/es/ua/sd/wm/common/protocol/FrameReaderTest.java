package es.ua.sd.wm.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameReaderTest {

    @Test
    void emptyStreamMeansTheConnectionEndedNormally() throws IOException {
        FrameReader reader = new FrameReader(new ByteArrayInputStream(new byte[0]));

        assertNull(reader.readFrame());
    }

    @Test
    void readsAFrameDeliveredInSeparateParts() throws IOException {
        byte[] frame = FrameCodec.encode("HELLO");
        ByteArrayInputStream firstPart = new ByteArrayInputStream(Arrays.copyOfRange(frame, 0, 3));
        ByteArrayInputStream secondPart = new ByteArrayInputStream(Arrays.copyOfRange(frame, 3, frame.length));
        FrameReader reader = new FrameReader(new SequenceInputStream(firstPart, secondPart));

        assertArrayEquals(frame, reader.readFrame());
        assertNull(reader.readFrame());
    }

    @Test
    void readsJoinedFramesWithoutConsumingTheNextFrame() throws IOException {
        byte[] firstFrame = FrameCodec.encode("HELLO");
        byte[] secondFrame = FrameCodec.encode("PING#1");
        ByteArrayOutputStream joinedFrames = new ByteArrayOutputStream();
        joinedFrames.write(firstFrame);
        joinedFrames.write(secondFrame);
        FrameReader reader = new FrameReader(new ByteArrayInputStream(joinedFrames.toByteArray()));

        assertArrayEquals(firstFrame, reader.readFrame());
        assertArrayEquals(secondFrame, reader.readFrame());
        assertNull(reader.readFrame());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A@", "AA"})
    void checksumCanHaveTheSameValueAsAFrameDelimiter(String message) throws IOException {
        // These messages produce an LRC of STX and ETX respectively.
        byte[] frame = FrameCodec.encode(message);
        FrameReader reader = new FrameReader(new ByteArrayInputStream(frame));

        assertArrayEquals(frame, reader.readFrame());
        assertNull(reader.readFrame());
    }

    @Test
    void rejectsBytesThatDoNotStartAFrame() {
        byte[] bytes = {'H', 'E', 'L', 'L', 'O'};
        FrameReader reader = new FrameReader(new ByteArrayInputStream(bytes));

        assertThrows(IOException.class, reader::readFrame);
    }

    @Test
    void rejectsAConnectionEndingInsideTheMessage() {
        byte[] partialFrame = {0x02, 'H', 'E'};
        FrameReader reader = new FrameReader(new ByteArrayInputStream(partialFrame));

        assertThrows(EOFException.class, reader::readFrame);
    }

    @Test
    void rejectsAConnectionEndingBeforeTheChecksum() {
        byte[] partialFrame = {0x02, 'H', 0x03};
        FrameReader reader = new FrameReader(new ByteArrayInputStream(partialFrame));

        assertThrows(EOFException.class, reader::readFrame);
    }

    @Test
    void leavesChecksumValidationToTheCodec() throws IOException {
        byte[] corruptedFrame = {0x02, 'H', 'E', 'L', 'L', 'O', 0x03, 0x40};
        FrameReader reader = new FrameReader(new ByteArrayInputStream(corruptedFrame));

        byte[] receivedFrame = reader.readFrame();

        assertArrayEquals(corruptedFrame, receivedFrame);
        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(receivedFrame));
    }

    @Test
    void acceptsAMessageExactlyAtTheConfiguredLimit() throws IOException {
        byte[] frame = FrameCodec.encode("HELLO");
        FrameReader reader = new FrameReader(new ByteArrayInputStream(frame), 5);

        assertArrayEquals(frame, reader.readFrame());
    }

    @Test
    void rejectsAMessageLongerThanTheConfiguredLimit() {
        byte[] frame = FrameCodec.encode("HELLO");
        FrameReader reader = new FrameReader(new ByteArrayInputStream(frame), 4);

        assertThrows(IOException.class, reader::readFrame);
    }

    @Test
    void rejectsANullInputStream() {
        assertThrows(NullPointerException.class, () -> new FrameReader(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsANonPositiveMessageLengthLimit(int limit) {
        assertThrows(IllegalArgumentException.class,
                () -> new FrameReader(new ByteArrayInputStream(new byte[0]), limit));
    }

    @Test
    void readsFramesFromARealTcpConnection() throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket server = new ServerSocket(0, 1, loopback);
             Socket sender = new Socket(loopback, server.getLocalPort());
             Socket receiver = server.accept()) {
            receiver.setSoTimeout(2000);
            byte[] frame = FrameCodec.encode("PING#1");
            sender.getOutputStream().write(frame, 0, 3);
            sender.getOutputStream().flush();
            sender.getOutputStream().write(frame, 3, frame.length - 3);
            sender.shutdownOutput();
            FrameReader reader = new FrameReader(receiver.getInputStream());

            assertEquals("PING#1", FrameCodec.decode(reader.readFrame()));
            assertNull(reader.readFrame());
        }
    }
}
