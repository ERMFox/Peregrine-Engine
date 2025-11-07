package ch.ermfox.encoding;

import java.util.Base64;

/**
 * Utility class providing Base64 and Base64-URL encoding helpers.
 *
 * <p>This class is used by the execution pipeline to serialize binary
 * plugin output into transport-safe strings. It does not alter input
 * data beyond the encoding step and performs no encryption.
 *
 * <p>All methods return continuous encoded strings with no line wrapping.
 */
public class Base64Functions {

    /**
     * Encodes the given bytes using standard Base64 encoding.
     *
     * <p>Resulting strings include {@code +}, {@code /}, and {@code =} padding.
     * This encoding is suitable for most textual transport (e.g., JSON, logs).
     *
     * @param data raw bytes to encode
     * @return Base64-encoded string
     */
    public String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Encodes the given bytes using URL-safe Base64 encoding.
     *
     * <p>The output replaces:
     * <ul>
     *   <li>{@code + → -}</li>
     *   <li>{@code / → _}</li>
     * </ul>
     *
     * <p>Padding {@code =} may still be included depending on JVM implementation.
     * This form is suitable for URLs, filenames, query parameters, or systems
     * that restrict standard Base64 characters.
     *
     * @param data raw bytes to encode
     * @return Base64-URL-safe encoded string
     */
    public String encodeURLSafe(byte[] data) {
        return Base64.getUrlEncoder().encodeToString(data);
    }
}
