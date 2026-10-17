package es.ua.sd.wm.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameCodecTest {

    @Test
    void encodeRejectsNullWithTheOriginalErrorMessage() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> FrameCodec.encode(null));

        assertEquals("Message must not be null", exception.getMessage());
    }

    @Test
    void decodeRejectsNullWithTheOriginalErrorMessage() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> FrameCodec.decode(null));

        assertEquals("Frame must not be null", exception.getMessage());
    }

    @Test
    void encodeAddsDelimitersAndChecksum() {
        // XOR of HELLO and ETX is 0x41.
        byte[] expectedFrame = {0x02, 'H', 'E', 'L', 'L', 'O', 0x03, 0x41};

        assertArrayEquals(expectedFrame, FrameCodec.encode("HELLO"));
    }

    @Test
    void decodeReadsAValidFrame() {
        byte[] frame = {0x02, 'H', 'E', 'L', 'L', 'O', 0x03, 0x41};

        assertEquals("HELLO", FrameCodec.decode(frame));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HELLO", "PING#1", "AUTH#WS-01#Park North"})
    void messageSurvivesEncodingAndDecoding(String message) {
        byte[] frame = FrameCodec.encode(message);

        assertEquals(message, FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsCorruptedData() {
        byte[] frame = {0x02, 'H', 'E', 'L', 'L', 'P', 0x03, 0x41};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsAnIncorrectChecksum() {
        byte[] frame = {0x02, 'H', 'E', 'L', 'L', 'O', 0x03, 0x40};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsAMissingStartDelimiter() {
        byte[] frame = {0x00, 'H', 'E', 'L', 'L', 'O', 0x03, 0x41};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsAMissingEndDelimiter() {
        byte[] frame = {0x02, 'H', 'E', 'L', 'L', 'O', 0x00, 0x41};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsATruncatedFrame() {
        byte[] frame = {0x02, 'H', 0x03};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsAnEmptyMessage() {
        byte[] frame = {0x02, 0x03, 0x03};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @Test
    void decodeRejectsNonAsciiDataEvenWithAValidChecksum() {
        byte[] frame = {0x02, (byte) 0xFF, 0x03, (byte) 0xFC};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PING\n", "ID#Estación", "PING\u0003"})
    void encodeRejectsEmptyMessagesAndNonPrintableAscii(String message) {
        assertThrows(IllegalArgumentException.class, () -> FrameCodec.encode(message));
    }

    @Test
    void decodeRejectsControlCharactersEvenWithAValidChecksum() {
        byte[] frame = {0x02, 0x03, 0x03, 0x00};

        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(frame));
    }
}
