package ch.ermfox.providers;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Simple wrapper around a Dotenv instance that provides access
 * to environment variables and values defined in a .env file.
 *
 * <p>This abstraction allows the execution pipeline to consistently
 * obtain secrets such as:
 * <ul>
 *     <li>Master HMAC key ({@code SECRET_KEY})</li>
 *     <li>Plugin signature values ({@code PLUGIN_SIG_*})</li>
 * </ul>
 *
 * <p>The provider loads the .env file exactly once at startup.
 */
public final class EnvProvider {

    /** Singleton Dotenv instance used for value lookup. */
    private static final Dotenv DOTENV = Dotenv.load();

    /** Prevent instantiation. */
    private EnvProvider() {}

    /**
     * Retrieves the value associated with the given key from the environment
     * or .env file.
     *
     * <p>If the key is not present, this method returns {@code null}.
     *
     * @param key name of the .env or environment variable
     * @return the value, or {@code null} if not defined
     */
    public static String get(String key) {
        return DOTENV.get(key);
    }
}
