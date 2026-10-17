package es.ua.sd.wm.common.validation;

/** Reusable input checks shared by all WaterManagement applications. */
public final class Validator {

    private static final char FIRST_PRINTABLE_ASCII_CHARACTER = ' ';
    private static final char LAST_PRINTABLE_ASCII_CHARACTER = '~';

    private Validator() {
    }

    public static void requireMaximumLength(String value, int maximumLength, String fieldName) {
        requireNonNull(value, fieldName);
        requirePositive(maximumLength, "Maximum length");

        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must contain at most " + maximumLength + " characters");
        }
    }

    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }

    public static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }
    }

    public static void requireNonEmpty(String value, String fieldName) {
        requireNonNull(value, fieldName);

        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    /** Checks characters only; use requireNonEmpty as well when empty text is forbidden. */
    public static void requirePrintableAscii(String value, String fieldName) {
        requireNonNull(value, fieldName);

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < FIRST_PRINTABLE_ASCII_CHARACTER || character > LAST_PRINTABLE_ASCII_CHARACTER) {
                throw new IllegalArgumentException(fieldName + " must contain only printable ASCII characters");
            }
        }
    }
}
