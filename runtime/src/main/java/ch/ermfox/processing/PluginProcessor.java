package ch.ermfox.processing;

import ch.ermfox.resources.PluginResult;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>Logging occurs only at orchestration boundaries. Plugins and utility
 * functions do not perform logging to avoid side effects and maintain deterministic
 * behavior. This class is <b>not a security sandbox</b>—plugins have full JVM access.
 *
 * @see Processor
 * @see PluginResult
 */
public class PluginProcessor {

    /** SLF4J logger for orchestration-level operations. */
    private static final Logger log = LoggerFactory.getLogger(PluginProcessor.class);

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
     * @param pluginPath filesystem path to the plugin JAR
     * @param timeoutMs maximum execution duration (milliseconds), or ≤ 0 for unbounded
     * @return result of plugin execution (success, timeout, or error)
     */
    public PluginResult executePlugin(Path pluginPath, long timeoutMs) {

        String mainClass = metaJson.has("pluginMainClass")
                ? metaJson.get("pluginMainClass").getAsString()
                : null;

        if (mainClass == null) {
            log.warn("Missing pluginMainClass in metadata; cannot execute plugin");
            return PluginResult.error("missing pluginMainClass in metadata");
        }

        log.debug("Preparing plugin '{}' main class='{}'", metaJson.get("pluginName"), mainClass);

        Callable<PluginResult> task = () -> {
            URL jarUrl = pluginPath.toUri().toURL();

            try (URLClassLoader loader =
                         new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader())) {

                log.trace("Loading plugin class '{}'", mainClass);
                Class<?> clazz = loader.loadClass(mainClass);

                Method executeMethod = clazz.getMethod(
                        "execute",
                        JsonObject.class,
                        JsonObject.class,
                        JsonObject.class
                );

                Object instance = clazz.getDeclaredConstructor().newInstance();
                log.trace("Instantiated plugin '{}'", mainClass);

                log.debug("Invoking plugin method execute(meta, input, settings)");
                Object result = executeMethod.invoke(instance, metaJson, inputJson, settingsJson);

                if (result == null) {
                    log.warn("Plugin '{}' returned null", mainClass);
                    return PluginResult.error("plugin returned null");
                }
                if (!(result instanceof byte[] bytes)) {
                    log.warn("Plugin '{}' returned non-byte[] value: {}", mainClass, result.getClass());
                    return PluginResult.error("plugin returned non-byte[] type");
                }

                log.info("Plugin '{}' completed successfully", mainClass);
                return PluginResult.ok(bytes);
            }
            catch (Exception e) {
                log.error("Plugin '{}' threw exception: {}", mainClass, e.toString());
                return PluginResult.error("plugin exception: " + e.getMessage());
            }
        };

        Future<PluginResult> future = executor.submit(task);

        try {
            if (timeoutMs > 0) {
                log.debug("Waiting for plugin completion with timeout={}ms", timeoutMs);
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                log.debug("Waiting for plugin completion with no timeout");
                return future.get();
            }
        } catch (TimeoutException te) {
            log.error("Plugin '{}' timed out after {}ms", mainClass, timeoutMs);
            future.cancel(true);
            return PluginResult.timeout();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Plugin '{}' execution interrupted: {}", mainClass, e.toString());
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
        log.debug("Shutting down plugin executor");
        executor.shutdownNow();
    }
}
