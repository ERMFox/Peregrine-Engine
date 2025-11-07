package ch.ermfox.processing;

import ch.ermfox.crypto.AESFunctions;
import ch.ermfox.crypto.HMACFunctions;
import ch.ermfox.encoding.Base64Functions;
import ch.ermfox.providers.EnvProvider;
import ch.ermfox.providers.LocalFileDataProvider;
import ch.ermfox.resources.PluginResult;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Core executor for Peregrine-Engine.
 *
 * <p>This class orchestrates the full execution lifecycle of a plugin:
 * <ol>
 *   <li>Resolve plugin JAR location</li>
 *   <li>Verify plugin integrity via HMAC-SHA256</li>
 *   <li>Optionally decrypt input (AES-GCM)</li>
 *   <li>Execute the plugin with timeout and reflective isolation</li>
 *   <li>Optionally encrypt output (AES-GCM)</li>
 *   <li>Encode output as Base64 or Base64-URL</li>
 * </ol>
 *
 * <p>Execution input is controlled via three JSON objects:
 * <ul>
 *   <li>{@code meta}</li>
 *   <li>{@code input}</li>
 *   <li>{@code settings}</li>
 * </ul>
 *
 * <p>Logging is performed only at orchestration boundaries. Crypto/HMAC helpers
 * do not perform logging to ensure correctness and minimal side-effects.
 *
 * <p>This class does not sandbox plugins — they run with full JVM privileges.
 *
 * @see PluginProcessor
 * @see PluginResult
 */
public class Processor {

    /** SLF4J logger for orchestration-level reporting. */
    private static final Logger log = LoggerFactory.getLogger(Processor.class);

    /** Default timeout applied if not provided via settings or environment. */
    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    private final JsonObject metaJson;
    private final JsonObject inputJson;
    private final JsonObject settingsJson;

    private LocalFileDataProvider provider = new LocalFileDataProvider();
    private HMACFunctions hmacFunctions = new HMACFunctions();
    private AESFunctions aesFunctions = new AESFunctions();
    private Base64Functions base64Functions = new Base64Functions();

    /**
     * AES/HMAC secret key derived from {@code SECRET_KEY} environment variable.
     * This must be provided by the embedding runtime.
     */
    private final byte[] secretKey =
            AESFunctions.deriveKey(EnvProvider.get("SECRET_KEY")).getEncoded();

    /**
     * Constructs a new Processor instance.
     *
     * @param metaJson     plugin metadata & execution flags
     * @param inputJson    primary input payload
     * @param settingsJson plugin execution settings
     */
    public Processor(JsonObject metaJson, JsonObject inputJson, JsonObject settingsJson) {
        this.metaJson = metaJson;
        this.inputJson = inputJson;
        this.settingsJson = settingsJson;
    }

    /**
     * Executes the full plugin pipeline.
     *
     * @return Base64/Base64URL-encoded plugin output, or a descriptive error string
     */
    public String process() {

        String pluginName = metaJson.get("pluginName").getAsString();
        String pluginLocation = metaJson.get("fileLocation").getAsString();
        Path pluginPath = provider.resolve(pluginLocation);

        log.info("Starting processing for plugin '{}' at path '{}'", pluginName, pluginLocation);

        if (!Files.exists(pluginPath)) {
            log.error("Plugin JAR does not exist or cannot be accessed: {}", pluginPath);
            return "plugin doesn't exist or no permissions to access file";
        }

        if (!verifyPlugin(pluginPath, pluginName)) {
            log.warn("HMAC signature verification failed for plugin '{}'", pluginName);
            return "plugin verification failed";
        }
        log.debug("HMAC signature verification passed for plugin '{}'", pluginName);

        decryptInputIfNeeded();

        long timeoutMs = resolveTimeoutMs();
        log.debug("Resolved timeout={}ms for plugin '{}'", timeoutMs, pluginName);

        PluginProcessor pluginProcessor = new PluginProcessor(metaJson, inputJson, settingsJson);

        log.info("Executing plugin '{}' with timeout={}ms", pluginName, timeoutMs);
        PluginResult result = pluginProcessor.executePlugin(pluginPath, timeoutMs);

        if (!result.ok) {
            if (result.timedOut) {
                log.error("Plugin '{}' timed out after {}ms", pluginName, timeoutMs);
                return "plugin timed out";
            }
            log.error("Plugin '{}' execution failed: {}", pluginName, result.error);
            return "plugin execution failed: " + result.error;
        }

        log.info("Plugin '{}' executed successfully", pluginName);

        byte[] payload = result.payload;

        if (metaJson.has("encryptOutput") && metaJson.get("encryptOutput").getAsBoolean()) {
            log.debug("Encrypting output for plugin '{}'", pluginName);
            payload = aesFunctions.encryptString(new String(payload, StandardCharsets.UTF_8), secretKey);
        }

        boolean urlSafe = metaJson.has("urlSafeOutput") && metaJson.get("urlSafeOutput").getAsBoolean();
        log.debug("Encoding output (urlSafe={}) for plugin '{}'", urlSafe, pluginName);

        return urlSafe
                ? base64Functions.encodeURLSafe(payload)
                : base64Functions.encode(payload);
    }

    /**
     * Resolves plugin timeout in the following priority order:
     * <ol>
     *   <li>{@code settings.timeoutMs}</li>
     *   <li>{@code PLUGIN_TIMEOUT_MS} environment variable</li>
     *   <li>{@code DEFAULT_TIMEOUT_MS}</li>
     * </ol>
     *
     * @return timeout in milliseconds
     */
    private long resolveTimeoutMs() {
        try {
            if (settingsJson.has("timeoutMs")) {
                long t = settingsJson.get("timeoutMs").getAsLong();
                log.trace("Using timeout from settings.json: {}", t);
                return t;
            }
        } catch (Exception ignored) {
            log.warn("Failed to parse timeoutMs from settings; falling back");
        }

        String envTimeout = EnvProvider.get("PLUGIN_TIMEOUT_MS");
        if (envTimeout != null) {
            try {
                long t = Long.parseLong(envTimeout);
                log.trace("Using timeout from environment: {}", t);
                return t;
            } catch (NumberFormatException ignored) {
                log.warn("Invalid PLUGIN_TIMEOUT_MS value: '{}'", envTimeout);
            }
        }

        log.trace("Using default timeout: {}ms", DEFAULT_TIMEOUT_MS);
        return DEFAULT_TIMEOUT_MS;
    }

    /**
     * Verifies plugin JAR integrity based on its registered HMAC signature.
     *
     * @param pluginPath filesystem location of plugin JAR
     * @param pluginName logical plugin identifier
     * @return true if HMAC matches expected value; false otherwise
     */
    private boolean verifyPlugin(Path pluginPath, String pluginName) {
        try {
            byte[] pluginBytes = provider.read(pluginPath);
            String keyName = "PLUGIN_SIG_" + pluginName.replace("-", "_").toUpperCase();
            String expectedSigB64 = EnvProvider.get(keyName);
            if (expectedSigB64 == null) {
                log.warn("No expected signature found for '{}' (env='{}')", pluginName, keyName);
                return false;
            }
            byte[] expectedSig = Base64.getDecoder().decode(expectedSigB64);

            boolean ok = hmacFunctions.verifyHmac(secretKey, pluginBytes, expectedSig);
            if (!ok) {
                log.warn("Plugin signature mismatch for '{}'", pluginName);
            }
            return ok;
        } catch (Exception e) {
            log.error("Failed verifying plugin '{}': {}", pluginName, e.getMessage());
            return false;
        }
    }

    /**
     * Decrypts input payload in-place if {@code encryptedInput=true}.
     * Replaces {@code input.data} with decrypted plaintext.
     */
    private void decryptInputIfNeeded() {
        if (!metaJson.get("encryptedInput").getAsBoolean()) return;

        log.debug("Decrypting plugin input payload");
        String encryptedB64 = inputJson.get("data").getAsString();
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedB64);
        String plaintext = aesFunctions.decryptString(encryptedBytes, secretKey);
        inputJson.addProperty("data", plaintext);
    }
}
