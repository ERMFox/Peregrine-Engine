package ch.ermfox.processing;

import ch.ermfox.resources.PluginResult;
import com.google.gson.JsonObject;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * Responsible for loading and executing a plugin JAR in an isolated classloader.
 *
 * <p>This class performs the following operations:
 * <ol>
 *   <li>Loads the plugin JAR from disk using a {@link URLClassLoader}</li>
 *   <li>Reflectively locates the {@code execute(JsonObject, JsonObject, JsonObject)} method</li>
 *   <li>Instantiates the plugin via its no-arg constructor</li>
 *   <li>Invokes the execute method and captures the result</li>
 *   <li>Returns a {@link PluginResult} representing success, error, or timeout</li>
 * </ol>
 *
 * <p>Plugin execution is wrapped in a {@link Callable} submitted to an
 * {@link ExecutorService}. Each invocation is bounded by a configurable timeout
 * (milliseconds). If execution exceeds this timeout, the plugin thread is cancelled
 * and a timed-out {@code PluginResult} is returned.
 *
 * <h3>Expected Plugin Contract</h3>
 * A valid plugin must:
 * <ul>
 *   <li>Provide a no-argument constructor</li>
 *   <li>Expose a public method:</li>
 * </ul>
 * <pre>{@code
 * public byte[] execute(JsonObject meta, JsonObject input, JsonObject settings)
 * }</pre>
 *
 * <p>If the method returns {@code null}, or returns a type other than {@code byte[]},
 * execution is considered a failure and an error result is returned.
 *
 * <h3>Threading + Isolation</h3>
 * Each plugin execution:
 * <ul>
 *   <li>Uses a dedicated classloader so classes/resources inside the JAR are separate from the engine</li>
 *   <li>Runs asynchronously via a thread in a cached executor</li>
 * </ul>
 * <p>This is <b>not a security sandbox</b>: plugins can still access filesystem, network, reflection, etc.
 *
 * @see Processor
 * @see PluginResult
 */
public class PluginProcessor {

    /** Metadata JSON controlling plugin configuration and execution options. */
    private final JsonObject metaJson;

    /** JSON payload passed directly to the plugin. */
    private final JsonObject inputJson;

    /** Optional settings JSON passed to the plugin. */
    private final JsonObject settingsJson;

    /**
     * Executor used for asynchronous plugin execution. Cached thread pool
     * allows concurrent execution with aggressive reuse.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Constructs a {@code PluginProcessor} using the given metadata,
     * input payload, and settings.
     *
     * @param metaJson     metadata describing plugin (e.g., class name)
     * @param inputJson    primary input payload
     * @param settingsJson arbitrary additional settings
     */
    public PluginProcessor(JsonObject metaJson, JsonObject inputJson, JsonObject settingsJson) {
        this.metaJson = metaJson;
        this.inputJson = inputJson;
        this.settingsJson = settingsJson;
    }

    /**
     * Loads the plugin JAR located at {@code pluginPath}, executes its
     * {@code execute(...)} method, and returns the result as {@link PluginResult}.
     *
     * <p>Execution behavior:
     * <ul>
     *   <li>If {@code pluginMainClass} is missing → error</li>
     *   <li>If plugin class cannot be instantiated → error</li>
     *   <li>If {@code execute()} throws → error</li>
     *   <li>If return type is not byte[] → error</li>
     *   <li>If timeout expires → timeout result</li>
     *   <li>Otherwise → success</li>
     * </ul>
     *
     * <p>A new {@link URLClassLoader} is used per invocation to isolate plugin
     * bytecode. The loader is closed after execution.
     *
     * @param pluginPath filesystem path to the plugin JAR
     * @param timeoutMs maximum execution duration (milliseconds), or ≤ 0 for unbounded
     * @return result of plugin execution (success, timeout, or error)
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

    /**
     * Attempts to shut down the executor immediately.
     *
     * <p>This should be called when the processor is no longer needed to
     * avoid thread leakage. Pending tasks are interrupted.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
