package ch.ermfox.JsonToCsvPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A simple business-oriented plugin that converts an array of JSON objects
 * into CSV text. Useful for exporting runtime data to formats compatible
 * with Excel / Google Sheets.
 *
 * <p>Contract:
 * <ul>
 *   <li>Input must contain {@code input.data} as a JSON array</li>
 *   <li>Objects are expected to have flat key:value pairs</li>
 *   <li>Column order is determined from the union of all object keys</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * input.data = [
 *   { "name": "Alice", "age": 30 },
 *   { "name": "Bob",   "age": 25 }
 * ]
 * →
 * name,age
 * Alice,30
 * Bob,25
 * </pre>
 *
 * <p>Returns UTF-8 encoded CSV data.
 */
public class JsonToCsvPlugin {

    public byte[] execute(JsonObject meta, JsonObject input, JsonObject settings) {
        if (!input.has("data") || !input.get("data").isJsonArray()) {
            return error("input.data must be a JSON array");
        }

        JsonArray arr = input.getAsJsonArray("data");
        if (arr.size() == 0) {
            return new byte[0];
        }

        // Collect union of keys in order of appearance
        Set<String> headers = new LinkedHashSet<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                el.getAsJsonObject().keySet().forEach(headers::add);
            }
        }

        StringBuilder sb = new StringBuilder();

        // Header row
        sb.append(String.join(",", headers)).append("\n");

        // Rows
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            boolean first = true;
            for (String key : headers) {
                if (!first) sb.append(",");
                first = false;

                JsonElement v = obj.get(key);
                sb.append(v != null && !v.isJsonNull() ? escape(v.getAsString()) : "");
            }
            sb.append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] error(String msg) {
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        // Basic CSV escaping:
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
