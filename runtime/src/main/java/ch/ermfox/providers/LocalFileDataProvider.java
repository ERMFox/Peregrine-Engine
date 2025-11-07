package ch.ermfox.providers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Default {@link FileDataProvider} implementation.
 *
 * <p>Resolves locations to local filesystem {@link Path} instances and
 * reads file contents directly via {@link Files#readAllBytes(Path)}.
 *
 * <p>This provider is appropriate for runtime environments where plugin
 * artifacts or other resources are staged locally.
 */
public class LocalFileDataProvider implements FileDataProvider {

    /**
     * Reads file content as raw bytes from the given filesystem path.
     *
     * @param path location of binary asset
     * @return raw file bytes
     * @throws IOException if the file cannot be accessed
     */
    @Override
    public byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    /**
     * Resolves a string location into a filesystem {@link Path}.
     *
     * <p>This implementation performs a simple {@code Path.of(location)}
     * resolution, without additional mapping.
     *
     * @param location file location string
     * @return resolved {@link Path}
     */
    @Override
    public Path resolve(String location) {
        return Path.of(location);
    }
}
