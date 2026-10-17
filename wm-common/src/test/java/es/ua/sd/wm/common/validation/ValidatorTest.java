package es.ua.sd.wm.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    @Test
    void requireMaximumLengthAcceptsTheBoundaryAndRejectsLongerText() {
        assertDoesNotThrow(() -> Validator.requireMaximumLength("HELLO", 5, "Message"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.requireMaximumLength("HELLO", 4, "Message"));

        assertEquals("Message must contain at most 4 characters", exception.getMessage());
    }

    @Test
    void requireMaximumLengthRejectsNullAndInvalidLimits() {
        assertThrows(NullPointerException.class, () -> Validator.requireMaximumLength(null, 5, "Message"));
        assertThrows(IllegalArgumentException.class, () -> Validator.requireMaximumLength("HELLO", 0, "Message"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void requirePositiveAcceptsPositiveNumbers(int value) {
        assertDoesNotThrow(() -> Validator.requirePositive(value, "Limit"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void requirePositiveRejectsZeroAndNegativeNumbers(int value) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.requirePositive(value, "Limit"));

        assertEquals("Limit must be greater than zero", exception.getMessage());
    }

    @Test
    void requireNonNullAcceptsDifferentObjectTypes() {
        assertDoesNotThrow(() -> Validator.requireNonNull("WS-01", "Station ID"));
        assertDoesNotThrow(() -> Validator.requireNonNull(new byte[0], "Frame"));
        assertDoesNotThrow(() -> Validator.requireNonNull(new Object(), "Value"));
    }

    @Test
    void requireNonNullRejectsNullWithTheFieldName() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.requireNonNull(null, "Frame"));

        assertEquals("Frame must not be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"WS-01", " "})
    void requireNonEmptyAcceptsTextIncludingSpaces(String value) {
        assertDoesNotThrow(() -> Validator.requireNonEmpty(value, "Station ID"));
    }

    @Test
    void requireNonEmptyRejectsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.requireNonEmpty(null, "Message"));

        assertEquals("Message must not be null", exception.getMessage());
    }

    @Test
    void requireNonEmptyRejectsEmptyTextWithTheFieldName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.requireNonEmpty("", "Message"));

        assertEquals("Message must not be empty", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ~", "AUTH#WS-01#Park North"})
    void requirePrintableAsciiAcceptsPrintableText(String value) {
        assertDoesNotThrow(() -> Validator.requirePrintableAscii(value, "Message"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PING\n", "PING\u0003", "\u007F", "Café"})
    void requirePrintableAsciiRejectsOtherCharacters(String value) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.requirePrintableAscii(value, "Message"));

        assertEquals("Message must contain only printable ASCII characters", exception.getMessage());
    }

    @Test
    void requirePrintableAsciiRejectsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.requirePrintableAscii(null, "Message"));

        assertEquals("Message must not be null", exception.getMessage());
    }
}
