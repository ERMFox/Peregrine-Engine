package ch.ermfox.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * AES-GCM encryption utilities used by Peregrine-Engine for optional payload
 * confidentiality.
 *
 * <p>This class provides:
 * <ul>
 *   <li>Authenticated encryption using AES/GCM/NoPadding</li>
 *   <li>IV generation and prepending for reconstruction during decryption</li>
 *   <li>SHA-256–based key derivation from a UTF-8 passphrase</li>
 * </ul>
 *
 * <h2>Security Notes</h2>
 * <ul>
 *   <li>This implementation uses AES-256-GCM when supplied with a 32-byte key.</li>
 *   <li>The IV (96 bits) is randomly generated per encryption as recommended for GCM.</li>
 *   <li>The returned encrypted payload is {@code IV || ciphertext}.</li>
 *   <li>No additional authenticated data (AAD) is currently used.</li>
 *   <li>
 *     {@code deriveKey()} performs a simple SHA-256 hash; for production use,
 *     a real KDF (e.g., PBKDF2, HKDF, Argon2) is recommended for stronger
 *     brute-force resistance.
 *   </li>
 * </ul>
 *
 * <p>These utilities do not provide transport encoding; callers typically wrap
 * encrypted output using Base64 for interchange (see Base64Functions).
 */
public class AESFunctions {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";

    /** GCM IV length in bytes (96 bits, NIST recommended). */
    private static final int GCM_IV_LENGTH = 12;

    /** GCM authentication tag length in bits. */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Encrypts a UTF-8 string using AES-GCM.
     *
     * <p>The output format is:
     * <pre>
     *   IV (12 bytes) || ciphertext+tag
     * </pre>
     *
     * @param plaintext UTF-8 string to encrypt
     * @param key AES key (16/24/32 bytes recommended; ideally 32 bytes)
     * @return byte[] containing IV prepended to ciphertext
     * @throws RuntimeException if encryption fails
     */
    public byte[] encryptString(String plaintext, byte[] key) {
        try {
            // generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // prepend IV so decrypt can read it later
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts an AES-GCM payload previously produced by {@link #encryptString}.
     *
     * <p>The input must contain:
     * <pre>
     *   IV (12 bytes) || ciphertext+tag
     * </pre>
     *
     * @param input byte[] containing IV + ciphertext
     * @param key AES key used during encryption
     * @return decrypted plaintext string (UTF-8)
     * @throws RuntimeException if decryption fails
     */
    public String decryptString(byte[] input, byte[] key) {
        try {
            // split IV + ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[input.length - GCM_IV_LENGTH];

            System.arraycopy(input, 0, iv, 0, iv.length);
            System.arraycopy(input, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }

    /**
     * Derives a static AES key from a UTF-8 passphrase using SHA-256.
     *
     * <p>This produces a deterministic 256-bit key. While sufficient for many
     * internal workflows, a hardened KDF (e.g., PBKDF2/HKDF/Argon2) is
     * recommended when deriving keys from user input or weak secrets.
     *
     * @param passphrase UTF-8 text used as key source
     * @return {@link SecretKey} (AES)
     * @throws RuntimeException if hashing fails
     */
    public static SecretKey deriveKey(String passphrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Key generation failed", e);
        }
    }
}
