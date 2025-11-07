package ch.ermfox.processing;

import ch.ermfox.crypto.AESFunctions;
import ch.ermfox.crypto.HMACFunctions;
import ch.ermfox.encoding.Base64Functions;
import ch.ermfox.providers.EnvProvider;
import ch.ermfox.providers.LocalFileDataProvider;
import ch.ermfox.resources.PluginResult;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Core executor for Peregrine-Engine.
 *
 * <p>The {@code Processor} orchestrates the full execution lifecycle of a plugin:
 * <ol>
 *   <li>Resolve plugin JAR location</li>
 *   <li>Verify plugin integrity via HMAC-SHA256</li>
 *   <li>Optionally decrypt input</li>
 *   <li>Execute the plugin with timeout and isolation</li>
 *   <li>Optionally encrypt output</li>
 *   <li>Encode output as Base64 or Base64-URL</li>
 * </ol>
 *
 * <p>All execution configuration is supplied via three JSON objects:
 * <ul>
 *   <li>{@code meta} — plugin metadata and flags</li>
 *   <li>{@code input} — plugin input data</li>
 *   <li>{@code settings} — user/implementation-specific configuration</li>
 * </ul>
 *
 * <p>The final return value of {@link #process()} is always a string, representing
 * either a successful result (encoded payload) or an error string describing the failure.
 *
 * <p>This class does not perform sandboxing. Plugins executed via
 * {@link PluginProcessor} have full JVM access unless isolated externally.
 *
 * @see PluginProcessor
 * @see PluginResult
 */
public class Processor {

    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    private final JsonObject metaJson;
    private final JsonObject inputJson;
    private final JsonObject settingsJson;

    private LocalFileDataProvider provider = new LocalFileDataProvider();
    private HMACFunctions hmacFunctions = new HMACFunctions();
    private AESFunctions aesFunctions = new AESFunctions();
    private Base64Functions base64Functions = new Base64Functions();

    private final byte[] secretKey =
            AESFunctions.deriveKey(EnvProvider.get("SECRET_KEY")).getEncoded();

    public Processor(JsonObject metaJson, JsonObject inputJson, JsonObject settingsJson) {
        this.metaJson = metaJson;
        this.inputJson = inputJson;
        this.settingsJson = settingsJson;
    }

    /**
     * Executes the full plugin pipeline.
     */
    public String process() {

        Path pluginPath = provider.resolve(metaJson.get("fileLocation").getAsString());
        if (!Files.exists(pluginPath)) {
            return "plugin doesn't exist or no permissions to access file";
        }

        String pluginName = metaJson.get("pluginName").getAsString();
        if (!verifyPlugin(pluginPath, pluginName)) {
            return "plugin verification failed";
        }

        decryptInputIfNeeded();

        // ✅ NEW — resolve timeout
        long timeoutMs = resolveTimeoutMs();

        PluginProcessor pluginProcessor = new PluginProcessor(metaJson, inputJson, settingsJson);
        PluginResult result = pluginProcessor.executePlugin(pluginPath, timeoutMs);

        if (!result.ok) {
            if (result.timedOut) {
                return "plugin timed out";
            }
            return "plugin execution failed: " + result.error;
        }

        byte[] payload = result.payload;

        if (metaJson.has("encryptOutput") && metaJson.get("encryptOutput").getAsBoolean()) {
            payload = aesFunctions.encryptString(new String(payload, StandardCharsets.UTF_8), secretKey);
        }

        boolean urlSafe = metaJson.has("urlSafeOutput") && metaJson.get("urlSafeOutput").getAsBoolean();
        return urlSafe
                ? base64Functions.encodeURLSafe(payload)
                : base64Functions.encode(payload);
    }

    /**
     * Timeout resolution hierarchy:
     * 1) settings.timeoutMs
     * 2) env var PLUGIN_TIMEOUT_MS
     * 3) DEFAULT_TIMEOUT_MS
     */
    private long resolveTimeoutMs() {
        try {
            if (settingsJson.has("timeoutMs")) {
                return settingsJson.get("timeoutMs").getAsLong();
            }
        } catch (Exception ignored) {}

        String envTimeout = EnvProvider.get("PLUGIN_TIMEOUT_MS");
        if (envTimeout != null) {
            try {
                return Long.parseLong(envTimeout);
            } catch (NumberFormatException ignored) {}
        }

        return DEFAULT_TIMEOUT_MS;
    }

    private boolean verifyPlugin(Path pluginPath, String pluginName) {
        try {
            byte[] pluginBytes = provider.read(pluginPath);
            String keyName = "PLUGIN_SIG_" + pluginName.replace("-", "_").toUpperCase();
            String expectedSigB64 = EnvProvider.get(keyName);
            if (expectedSigB64 == null) return false;
            byte[] expectedSig = Base64.getDecoder().decode(expectedSigB64);
            return hmacFunctions.verifyHmac(secretKey, pluginBytes, expectedSig);
        } catch (Exception e) {
            return false;
        }
    }

    private void decryptInputIfNeeded() {
        if (!metaJson.get("encryptedInput").getAsBoolean()) return;

        String encryptedB64 = inputJson.get("data").getAsString();
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedB64);
        String plaintext = aesFunctions.decryptString(encryptedBytes, secretKey);
        inputJson.addProperty("data", plaintext);
    }
}
