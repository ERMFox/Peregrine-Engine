package ch.ermfox.providers;

import io.github.cdimascio.dotenv.Dotenv;

public final class EnvProvider {

    private static final Dotenv DOTENV = Dotenv.load();

    private EnvProvider() {}

    public static String get(String key) {
        return DOTENV.get(key);
    }
}
