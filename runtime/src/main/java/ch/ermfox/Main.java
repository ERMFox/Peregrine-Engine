package ch.ermfox;

import ch.ermfox.processing.Processor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Entry point for Peregrine-Engine.
 *
 * <p>This class provides two primary execution modes:
 * <ul>
 *     <li><b>CLI mode</b>: Reads a JSON object from standard input containing
 *     {@code meta}, {@code input}, and {@code settings} objects.</li>
 *     <li><b>Embedded mode</b>: Exposes {@link #reflectedMain(String, String, String)}
 *     so that Java applications can invoke the engine programmatically.</li>
 * </ul>
 *
 * <p>Both modes normalize inputs into {@link JsonObject} instances, construct a
 * {@link Processor}, and call {@link Processor#process()} to execute the plugin
 * pipeline. The final result is emitted as a {@code String}, typically Base64 or
 * Base64-URL encoded content (optionally AES-GCM encrypted).
 *
 * <p>Usage:
 * <pre>
 *     // CLI example
 *     cat input.json | java -jar Peregrine-Engine.jar
 *
 *     // Embedded example
 *     String result = Main.reflectedMain(meta, input, settings);
 * </pre>
 */
public class Main {

    /** Shared GSON instance used for parsing JSON input. */
    private static Gson gson = new Gson();

    /**
     * CLI entry point.
     *
     * <p>If invoked with:
     * <ul>
     *     <li><b>0 arguments</b>: Expects a JSON object on stdin with {@code meta}, {@code input}, {@code settings} fields.</li>
     *     <li><b>3 arguments</b>: Expects each argument to be a JSON string representing {@code meta}, {@code input}, {@code settings}.</li>
     * </ul>
     *
     * <p>After parsing input, constructs a {@link Processor} instance and prints
     * the returned output to stdout.
     *
     * @param args Either 0 or 3 arguments providing input JSON.
     * @throws IllegalArgumentException if {@code args.length} is not 0 or 3
     */
    public static void main(String[] args) {
        JsonObject meta;
        JsonObject input;
        JsonObject settings;

        if (args.length == 0) {
            JsonObject piped = gson.fromJson(getPipedInputString(), JsonObject.class);
            meta     = piped.getAsJsonObject("meta");
            input    = piped.getAsJsonObject("input");
            settings = piped.getAsJsonObject("settings");

        } else if (args.length == 3) {
            meta     = gson.fromJson(args[0], JsonObject.class);
            input    = gson.fromJson(args[1], JsonObject.class);
            settings = gson.fromJson(args[2], JsonObject.class);

        } else {
            throw new IllegalArgumentException("Must provide 0 or 3 arguments");
        }

        Processor processor = new Processor(meta, input, settings);
        String result = processor.process();
        System.out.println(result);
    }

    /**
     * Programmatic entry point.
     *
     * <p>This method enables other JVM-based components (e.g. REST controllers,
     * message processors, job workers) to invoke Peregrine-Engine directly
     * without shelling out through the CLI.
     *
     * <p>All arguments must be valid JSON strings. Input contracts are identical
     * to the CLI format.
     *
     * @param meta     JSON string describing plugin metadata
     * @param input    JSON string containing plugin input payload
     * @param settings JSON string providing execution/environment settings
     * @return The final plugin result (Base64/Base64URL, optionally AES-GCM)
     */
    public static String reflectedMain(String meta, String input, String settings) {

        JsonObject metaJson     = gson.fromJson(meta, JsonObject.class);
        JsonObject inputJson    = gson.fromJson(input, JsonObject.class);
        JsonObject settingsJson = gson.fromJson(settings, JsonObject.class);

        Processor processor = new Processor(metaJson, inputJson, settingsJson);

        return processor.process();
    }

    /**
     * Reads all piped/streamed content from {@code System.in} as a single string.
     *
     * <p>Used when no command-line arguments are provided, enabling CLI pipelines
     * such as:
     * <pre>
     *     cat input.json | java -jar Peregrine-Engine.jar
     * </pre>
     *
     * @return Raw text read from standard input
     */
    private static String getPipedInputString() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.lines().collect(Collectors.joining("\n"));
    }
}
