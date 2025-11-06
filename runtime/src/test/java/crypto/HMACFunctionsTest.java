package crypto;

import ch.ermfox.crypto.HMACFunctions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HMACFunctionsTest {

    HMACFunctions hmac = new HMACFunctions();

    @Test
    void generatesValidMac() {
        byte[] key = "secret".getBytes();
        byte[] data = "Hello Sophie".getBytes();

        byte[] mac = hmac.hmacSha256(key, data);

        assertNotNull(mac);
        assertTrue(mac.length > 0);
    }

    @Test
    void verifyMatches() {
        byte[] key = "secret".getBytes();
        byte[] data = "Hello Sophie".getBytes();

        byte[] mac = hmac.hmacSha256(key, data);

        assertTrue(hmac.verifyHmac(key, data, mac));
    }

    @Test
    void verifyFailsOnDifferentData() {
        byte[] key = "secret".getBytes();
        byte[] data = "Hello Sophie".getBytes();
        byte[] data2 = "Hello Raelynn".getBytes();

        byte[] mac = hmac.hmacSha256(key, data);

        assertFalse(hmac.verifyHmac(key, data2, mac));
    }
}
