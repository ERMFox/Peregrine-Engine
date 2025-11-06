package ch.ermfox.resources;

public class PluginResult {
    public final boolean ok;
    public final byte[] payload;
    public final String error;
    public final boolean timedOut;

    private PluginResult(boolean ok, byte[] payload, String error, boolean timedOut) {
        this.ok = ok;
        this.payload = payload;
        this.error = error;
        this.timedOut = timedOut;
    }

    public static PluginResult ok(byte[] payload) {
        return new PluginResult(true, payload, null, false);
    }

    public static PluginResult error(String error) {
        return new PluginResult(false, null, error, false);
    }

    public static PluginResult timeout() {
        return new PluginResult(false, null, "timeout", true);
    }
}
