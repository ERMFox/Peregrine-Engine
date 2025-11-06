package encoding;

import ch.ermfox.encoding.Base64Functions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base64FunctionsTest {

    Base64Functions b64 = new Base64Functions();

    @Test
    void encodeBase64_shouldReturnCorrectString() {
        String input = "Hello Sophie <3";
        String encoded = b64.encode(input.getBytes());

        assertEquals("SGVsbG8gU29waGllIDwz", encoded);
    }

    @Test
    void encodeURLSafe_shouldUseUrlSafeAlphabet() {
        // Pick a string that forces + and / in regular Base64
        byte[] input = new byte[] { (byte)0xFB, (byte)0xEF, (byte)0xBF };

        // Standard
        String standard = b64.encode(input);

        // URL Safe
        String urlSafe = b64.encodeURLSafe(input);

        assertNotEquals(standard, urlSafe);

        // Ensure only URL-safe chars used
        assertFalse(urlSafe.contains("+"));
        assertFalse(urlSafe.contains("/"));
    }
}
