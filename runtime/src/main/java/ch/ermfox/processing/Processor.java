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

public class Processor {

    private final JsonObject metaJson;
    private final JsonObject inputJson;
    private final JsonObject settingsJson;

    private LocalFileDataProvider provider = new LocalFileDataProvider();
    private HMACFunctions hmacFunctions = new HMACFunctions();
    private AESFunctions aesFunctions = new AESFunctions();
    private Base64Functions base64Functions = new Base64Functions();

    private final byte[] secretKey = AESFunctions.deriveKey(EnvProvider.get("SECRET_KEY")).getEncoded();


    public Processor(JsonObject metaJson, JsonObject inputJson, JsonObject settingsJson) {
        this.metaJson = metaJson;
        this.inputJson = inputJson;
        this.settingsJson = settingsJson;
    }

    public String process() {

        Path pluginPath = provider.resolve(metaJson.get("fileLocation").getAsString());
        if (!Files.exists(pluginPath)) {
            return "plugin doesn't exist or no permissions to access file";
        }

        // 2) validate plugin signature
        String pluginName = metaJson.get("pluginName").getAsString();
        if (!verifyPlugin(pluginPath, pluginName)) {
            return "plugin verification failed";
        }

        // 3) decrypt input if needed
        decryptInputIfNeeded();

        // 4) execute plugin
        PluginProcessor pluginProcessor = new PluginProcessor(metaJson, inputJson, settingsJson);
        PluginResult result = pluginProcessor.executePlugin(pluginPath, 5000); // timeout 5s

        if (!result.ok) {
            if (result.timedOut) {
                return "plugin timed out";
            }
            return "plugin execution failed: " + result.error;
        }

        byte[] payload = result.payload;

        // 5) encrypt output if needed
        if (metaJson.has("encryptOutput") && metaJson.get("encryptOutput").getAsBoolean()) {
            payload = aesFunctions.encryptString(new String(payload, StandardCharsets.UTF_8), secretKey);
        }

        // 6) base64 encode (or URL-safe)
        boolean urlSafe = metaJson.has("urlSafeOutput") && metaJson.get("urlSafeOutput").getAsBoolean();
        String encoded = urlSafe
                ? base64Functions.encodeURLSafe(payload)
                : base64Functions.encode(payload);

        return encoded;
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
