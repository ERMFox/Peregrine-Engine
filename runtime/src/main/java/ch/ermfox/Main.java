package ch.ermfox;

import ch.ermfox.processing.Processor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class Main {
    private static Gson gson = new Gson();

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



    public static String reflectedMain(String meta, String input, String settings) {

        JsonObject metaJson     = gson.fromJson(meta, JsonObject.class);
        JsonObject inputJson    = gson.fromJson(input, JsonObject.class);
        JsonObject settingsJson = gson.fromJson(settings, JsonObject.class);

        Processor processor = new Processor(metaJson, inputJson, settingsJson);

        return processor.process();
    }



    private static String getPipedInputString() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.lines().collect(Collectors.joining("\n"));
    }

}