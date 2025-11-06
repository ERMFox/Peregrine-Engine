package ch.ermfox.processing;

import ch.ermfox.resources.PluginResult;
import com.google.gson.JsonObject;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.*;

public class PluginProcessor {

    private final JsonObject metaJson;
    private final JsonObject inputJson;
    private final JsonObject settingsJson;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PluginProcessor(JsonObject metaJson, JsonObject inputJson, JsonObject settingsJson) {
        this.metaJson = metaJson;
        this.inputJson = inputJson;
        this.settingsJson = settingsJson;
    }

    /**
     * Load + execute plugin JAR at pluginPath.
     * Returns PluginResult (success, timeout, or error).
     */
    public PluginResult executePlugin(Path pluginPath, long timeoutMs) {

        String mainClass = metaJson.has("pluginMainClass")
                ? metaJson.get("pluginMainClass").getAsString()
                : null;
        if (mainClass == null) {
            return PluginResult.error("missing pluginMainClass in metadata");
        }

        Callable<PluginResult> task = () -> {
            URL jarUrl = pluginPath.toUri().toURL();

            try (URLClassLoader loader =
                         new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader())) {

                Class<?> clazz = loader.loadClass(mainClass);

                Method executeMethod = clazz.getMethod(
                        "execute",
                        JsonObject.class,
                        JsonObject.class,
                        JsonObject.class
                );

                Object instance = clazz.getDeclaredConstructor().newInstance();
                Object result = executeMethod.invoke(instance, metaJson, inputJson, settingsJson);

                if (result == null) {
                    return PluginResult.error("plugin returned null");
                }
                if (!(result instanceof byte[] bytes)) {
                    return PluginResult.error("plugin returned non-byte[] type");
                }

                return PluginResult.ok(bytes);
            }
            catch (Exception e) {
                return PluginResult.error("plugin exception: " + e.getMessage());
            }
        };

        Future<PluginResult> future = executor.submit(task);

        try {
            if (timeoutMs > 0) {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                return future.get();
            }
        } catch (TimeoutException te) {
            future.cancel(true);
            return PluginResult.timeout();
        } catch (ExecutionException | InterruptedException e) {
            return PluginResult.error(e.toString());
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
