package crypto;

import ch.ermfox.crypto.HMACFunctions;
import ch.ermfox.providers.LocalFileDataProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HMACOrchestrationTest {

    @Test
    void verify_returnsTrue_whenSignatureMatches() throws Exception {
        HMACFunctions hmac = new HMACFunctions();
        LocalFileDataProvider provider = new LocalFileDataProvider();

        // Load real file
        Path path = Path.of("src/test/resources/test.txt");
        byte[] data = provider.read(path);

        byte[] key = "secret".getBytes();
        byte[] signature = hmac.hmacSha256(key, data);

        // verify
        boolean result = hmac.verifyHmac(key, data, signature);

        assertTrue(result);
    }

    @Test
    void verify_returnsFalse_whenSignatureMismatch() throws Exception {
        HMACFunctions hmac = new HMACFunctions();
        LocalFileDataProvider provider = new LocalFileDataProvider();

        Path path = Path.of("src/test/resources/test.txt");
        byte[] data = provider.read(path);

        byte[] key = "secret".getBytes();
        byte[] signature = hmac.hmacSha256(key, data);

        // modify data so it fails
        byte[] different = "Different data".getBytes();

        boolean result = hmac.verifyHmac(key, different, signature);

        assertFalse(result);
    }
}
