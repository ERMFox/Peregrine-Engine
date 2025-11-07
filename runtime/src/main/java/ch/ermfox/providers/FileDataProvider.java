package ch.ermfox.providers;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for loading raw binary data from an external source.
 *
 * <p>This interface defines how plugin JARs or other binary assets
 * can be retrieved during execution. Implementations may resolve data
 * from the local filesystem, cloud storage, in-memory storage, etc.
 *
 * <p>Only {@link #read(Path)} is required; {@link #resolve(String)}
 * provides a default mapping from a string location to a path.
 */
public interface FileDataProvider {

    /**
     * Reads all bytes from the specified path.
     *
     * @param path filesystem path to binary data
     * @return byte[] representing file contents
     * @throws IOException if the file cannot be read
     */
    byte[] read(Path path) throws IOException;

    /**
     * Resolves a location string into a {@link Path}.
     *
     * <p>Implementations may override this to support non-filesystem
     * resolution (e.g., S3 URIs, db keys, custom lookups).
     *
     * @param location a logical or raw location string
     * @return a resolved {@link Path}
     */
    default Path resolve(String location) {
        return Path.of(location);
    }
}
