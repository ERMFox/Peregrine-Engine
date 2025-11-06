package crypto;

import ch.ermfox.crypto.AESFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

class AESFunctionsTest {

    static AESFunctions aes;

    @BeforeAll
    static void setup() {
        aes = new AESFunctions();
    }

    @Test
    void encryptString_validInput_producesOutput() {
        byte[] key = new byte[16];
        String input = "Hello Sophie <3";

        byte[] encrypted = aes.encryptString(input, key);

        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0, "Encrypted data should not be empty");
    }

    @Test
    void encryptAndDecrypt_shouldReturnOriginalString() {
        byte[] key = new byte[16];
        String input = "Hello Sophie <3";

        byte[] encrypted = aes.encryptString(input, key);
        String decrypted = aes.decryptString(encrypted, key);

        assertEquals(input, decrypted);
    }

    @Test
    void decryptString_badKey_shouldFail() {
        byte[] key = new byte[16];
        String input = "Hello Sophie <3";

        byte[] encrypted = aes.encryptString(input, key);

        // wrong key
        byte[] wrongKey = new byte[16];
        wrongKey[0] = 1; // ensure different content

        assertThrows(RuntimeException.class, () -> {
            aes.decryptString(encrypted, wrongKey);
        });
    }

    @Test
    void deriveKey_fromPassphrase_hasCorrectLength() {
        SecretKey key = AESFunctions.deriveKey("hello world");

        assertEquals(32, key.getEncoded().length); // for 256-bit
    }


}
