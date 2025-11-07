package ch.ermfox.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Utility class providing HMAC-SHA256 generation and verification.
 *
 * <p>This class is used by Peregrine-Engine to verify plugin integrity:
 * plugins must be signed using HMAC-SHA256 of their raw JAR bytes,
 * using a shared secret key. The runtime recomputes the HMAC and checks
 * that it matches a reference signature supplied via environment variable.
 *
 * <h2>Security Notes</h2>
 * <ul>
 *   <li>HMAC-SHA256 provides message authentication + integrity.</li>
 *   <li>Verification uses {@link java.security.MessageDigest#isEqual} to avoid timing attacks.</li>
 *   <li>{@code key} should be at least 32 bytes (256-bit) for strong security.</li>
 * </ul>
 */
public class HMACFunctions {

    /** Standard Java algorithm name for HMAC-SHA256. */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Computes raw HMAC-SHA256 output.
     *
     * <p>No encoding is applied; callers may wrap output using Base64 or hex.
     *
     * @param key  secret key used to compute HMAC (typically 32-byte)
     * @param data data to authenticate
     * @return HMAC-SHA256 result as raw bytes
     * @throws RuntimeException if the algorithm or key initialization fails
     */
    public byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failure", e);
        }
    }

    /**
     * Computes HMAC-SHA256 and returns the output as Base64.
     *
     * @param key  secret key used to compute HMAC
     * @param data data to authenticate
     * @return Base64-encoded HMAC string
     */
    public String hmacSha256Base64(byte[] key, byte[] data) {
        return Base64.getEncoder().encodeToString(hmacSha256(key, data));
    }

    /**
     * Verifies that the expected HMAC value matches the computed one.
     *
     * <p>Comparison is performed using
     * {@link java.security.MessageDigest#isEqual(byte[], byte[])}
     * to minimize timing side-channels.
     *
     * @param key      secret key
     * @param data     data to authenticate
     * @param expected expected HMAC bytes
     * @return true if the computed HMAC matches {@code expected}, else false
     */
    public boolean verifyHmac(byte[] key, byte[] data, byte[] expected) {
        byte[] actual = hmacSha256(key, data);
        return java.security.MessageDigest.isEqual(actual, expected);
    }
}
