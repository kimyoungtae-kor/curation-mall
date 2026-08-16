package kr.co.petcuration.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemStorageServiceTests {

    @TempDir
    Path root;

    @Test
    void loadsOnlyRegularFilesInsideConfiguredRoot() throws Exception {
        Path image = root.resolve("demo/catalog/item.webp");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[] {1, 2, 3});
        FileSystemStorageService service = service();

        StoredMedia stored = service.find("/demo/catalog/item.webp").orElseThrow();

        assertThat(stored.contentLength()).isEqualTo(3);
        assertThat(stored.contentType().toString()).isEqualTo("image/webp");
        assertThat(stored.resource().exists()).isTrue();
    }

    @Test
    void rejectsTraversalAndMissingFiles() throws Exception {
        Path outside = root.getParent().resolve("outside.webp");
        Files.write(outside, new byte[] {9});
        FileSystemStorageService service = service();

        assertThat(service.find("../outside.webp")).isEmpty();
        assertThat(service.find("..\\outside.webp")).isEmpty();
        assertThat(service.find("missing.webp")).isEmpty();
    }

    private FileSystemStorageService service() {
        return new FileSystemStorageService(new StorageProperties(root, Duration.ofHours(1)));
    }
}
