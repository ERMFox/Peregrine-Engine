package ch.ermfox.encoding;

import java.util.Base64;

public class Base64Functions {

    public String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public String encodeURLSafe(byte[] data) {
        return Base64.getUrlEncoder().encodeToString(data);
    }
}
