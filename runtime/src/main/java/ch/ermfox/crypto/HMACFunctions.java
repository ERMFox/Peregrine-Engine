package ch.ermfox.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class HMACFunctions {

    private static final String HMAC_SHA256 = "HmacSHA256";

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

    public String hmacSha256Base64(byte[] key, byte[] data) {
        return Base64.getEncoder().encodeToString(hmacSha256(key, data));
    }

    public boolean verifyHmac(byte[] key, byte[] data, byte[] expected) {
        byte[] actual = hmacSha256(key, data);
        return java.security.MessageDigest.isEqual(actual, expected);
    }


}
