package ch.ermfox.resources;

/**
 * Represents the result of a plugin execution.
 *
 * <p>This container describes whether execution was successful, whether
 * a timeout occurred, and either provides a raw binary payload (success)
 * or an error string (failure).
 *
 * <p>The engine uses this object internally to distinguish between:
 * <ul>
 *   <li>Successful execution ({@code ok = true})</li>
 *   <li>Execution failure due to an error</li>
 *   <li>Execution cancelled due to timeout</li>
 * </ul>
 */
public class PluginResult {

    /**
     * Whether the plugin completed successfully.
     * <p>If {@code true}, {@link #payload} contains the returned bytes.
     */
    public final boolean ok;

    /**
     * Raw returned output from the plugin, if successful.
     * <p>May be {@code null} if execution failed or timed out.
     */
    public final byte[] payload;

    /**
     * Human-readable error message when execution fails.
     * <p>Present only if {@code ok = false} and not timed out.
     */
    public final String error;

    /**
     * Indicates whether the plugin was terminated because it exceeded
     * its allowed execution time.
     */
    public final boolean timedOut;

    private PluginResult(boolean ok, byte[] payload, String error, boolean timedOut) {
        this.ok = ok;
        this.payload = payload;
        this.error = error;
        this.timedOut = timedOut;
    }

    /**
     * Creates a successful result containing a plugin output payload.
     *
     * @param payload raw plugin output bytes
     * @return an OK {@code PluginResult}
     */
    public static PluginResult ok(byte[] payload) {
        return new PluginResult(true, payload, null, false);
    }

    /**
     * Creates an error result with a message but without payload.
     *
     * @param error human-readable error message
     * @return an error {@code PluginResult}
     */
    public static PluginResult error(String error) {
        return new PluginResult(false, null, error, false);
    }

    /**
     * Creates a timeout result.
     *
     * @return a timed-out {@code PluginResult}
     */
    public static PluginResult timeout() {
        return new PluginResult(false, null, "timeout", true);
    }
}
