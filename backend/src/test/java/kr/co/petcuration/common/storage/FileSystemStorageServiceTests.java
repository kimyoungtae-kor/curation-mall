package kr.co.petcuration.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    @Test
    void storesWithNestedKeyWithoutOverwritingExistingContent() throws Exception {
        FileSystemStorageService service = service();
        String key = "uploads/products/2026/08/image.png";

        StoredAsset stored = service.store(key, new byte[] {1, 2, 3});

        assertThat(stored.storageKey()).isEqualTo(key);
        assertThat(stored.contentLength()).isEqualTo(3);
        assertThat(Files.readAllBytes(root.resolve(key))).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> service.store(key, new byte[] {9}))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(root.resolve(key))).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsUnsafeWritableKeys() {
        FileSystemStorageService service = service();

        assertThatThrownBy(() -> service.store("../outside.png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store("uploads\\outside.png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store("/absolute.png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentStoresCanCreateTheSameDateDirectories() throws Exception {
        FileSystemStorageService service = service();
        int uploadCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(uploadCount)) {
            List<Future<StoredAsset>> uploads = new ArrayList<>();
            for (int index = 0; index < uploadCount; index++) {
                int fileNumber = index;
                uploads.add(executor.submit(() -> {
                    start.await();
                    return service.store(
                            "uploads/products/2026/08/concurrent-" + fileNumber + ".png",
                            new byte[] {(byte) fileNumber}
                    );
                }));
            }
            start.countDown();
            for (Future<StoredAsset> upload : uploads) {
                assertThat(upload.get().contentLength()).isEqualTo(1);
            }
        }

        try (var storedFiles = Files.list(root.resolve("uploads/products/2026/08"))) {
            assertThat(storedFiles).hasSize(uploadCount);
        }
    }

    private FileSystemStorageService service() {
        return new FileSystemStorageService(new StorageProperties(root, Duration.ofHours(1)));
    }
}
