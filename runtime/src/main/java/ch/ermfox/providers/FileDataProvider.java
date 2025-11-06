package ch.ermfox.providers;

import java.io.IOException;
import java.nio.file.Path;

public interface FileDataProvider {
    byte[] read(Path path) throws IOException;

    default Path resolve(String location) {
        return Path.of(location);
    }
}
