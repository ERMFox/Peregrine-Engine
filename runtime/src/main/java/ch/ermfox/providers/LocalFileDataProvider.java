package ch.ermfox.providers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFileDataProvider implements FileDataProvider {
    @Override
    public byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public Path resolve(String location) {
        return Path.of(location);   // local lookup
    }
}
