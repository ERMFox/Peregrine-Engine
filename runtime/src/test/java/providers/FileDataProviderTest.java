package providers;

import ch.ermfox.providers.FileDataProvider;
import ch.ermfox.providers.LocalFileDataProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;


public class FileDataProviderTest {

    @Test
    void FileProvider_readsCorrectBytes() throws Exception {
        Path path = Paths.get("src/test/resources/test.txt");
        FileDataProvider provider = new LocalFileDataProvider();

        byte[] bytes = provider.read(path);

        assertEquals("Hi", new String(bytes));
    }


}
