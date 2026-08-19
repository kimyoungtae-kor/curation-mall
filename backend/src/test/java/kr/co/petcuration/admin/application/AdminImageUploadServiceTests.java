package kr.co.petcuration.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.common.storage.ImageUploadProperties;
import kr.co.petcuration.common.storage.StorageService;
import kr.co.petcuration.common.storage.StoredAsset;
import kr.co.petcuration.common.storage.StoredMedia;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class AdminImageUploadServiceTests {

    @Test
    void pngIsResizedToConfiguredEdgeAndReencoded() throws Exception {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 10_000, 10_000, 40_000_000, 1_600);
        byte[] source = image("png", 2_000, 1_000);

        var uploaded = service.upload(new MockMultipartFile("file", "large.png", "image/png", source));

        assertThat(uploaded.width()).isEqualTo(1_600);
        assertThat(uploaded.height()).isEqualTo(800);
        assertThat(uploaded.contentType()).isEqualTo("image/png");
        assertThat(uploaded.storageKey()).matches(
                "uploads/products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        BufferedImage stored = ImageIO.read(new java.io.ByteArrayInputStream(storage.content));
        assertThat(stored.getWidth()).isEqualTo(1_600);
        assertThat(stored.getHeight()).isEqualTo(800);
    }

    @Test
    void jpegExifOrientationIsAppliedAndMetadataIsRemovedByReencoding() {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 10_000, 10_000, 40_000_000, 1_600);
        byte[] orientedJpeg = jpegWithOrientationSix();

        var uploaded = service.upload(new MockMultipartFile(
                "file", "phone-photo.jpg", "image/jpeg", orientedJpeg));

        assertThat(uploaded.width()).isEqualTo(1);
        assertThat(uploaded.height()).isEqualTo(2);
        assertThat(new String(storage.content, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("Exif");
    }

    @Test
    void validatedRepositoryWebpIsPreservedWithoutTranscoding() throws Exception {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 10_000, 10_000, 40_000_000, 1_600);
        byte[] webp = Files.readAllBytes(repositoryMedia("demo/catalog/oasis-water-bowl.webp"));

        var uploaded = service.upload(new MockMultipartFile("file", "bowl.webp", "image/webp", webp));

        assertThat(uploaded.width()).isPositive();
        assertThat(uploaded.height()).isPositive();
        assertThat(uploaded.contentType()).isEqualTo("image/webp");
        assertThat(storage.content).isEqualTo(webp);
    }

    @Test
    void oversizedPixelDimensionsAreRejectedBeforeStorage() {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 100, 100, 5_000, 100);
        byte[] image = image("png", 80, 80);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "large.png", "image/png", image)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(413);
                    assertThat(exception.getCode()).isEqualTo("IMAGE_DIMENSIONS_EXCEEDED");
                });
        assertThat(storage.content).isNull();
    }

    @Test
    void fileLargerThanEightMebibytesIsRejectedBeforeInspection() {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 10_000, 10_000, 40_000_000, 1_600);
        byte[] oversized = new byte[8 * 1024 * 1024 + 1];

        assertProblem(
                () -> service.upload(new MockMultipartFile("file", "large.png", "image/png", oversized)),
                413,
                "IMAGE_FILE_TOO_LARGE"
        );
        assertThat(storage.content).isNull();
    }

    @Test
    void fakeImageBytesAndDeclaredTypeMismatchAreRejected() {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 10_000, 10_000, 40_000_000, 1_600);

        assertProblem(
                () -> service.upload(new MockMultipartFile(
                        "file", "fake.png", "image/png", new byte[] {1, 2, 3})),
                415,
                "UNSUPPORTED_IMAGE_FORMAT"
        );
        assertProblem(
                () -> service.upload(new MockMultipartFile(
                        "file", "mismatch.jpg", "image/jpeg", image("png", 2, 2))),
                415,
                "IMAGE_CONTENT_TYPE_MISMATCH"
        );
        assertThat(storage.content).isNull();
    }

    @Test
    void webpExtendedHeaderWithoutImagePayloadIsRejected() {
        CapturingStorage storage = new CapturingStorage();
        AdminImageUploadService service = service(storage, 10_000, 10_000, 40_000_000, 1_600);

        assertProblem(
                () -> service.upload(new MockMultipartFile(
                        "file", "header-only.webp", "image/webp", vp8xHeaderOnly())),
                400,
                "INVALID_IMAGE"
        );
        assertThat(storage.content).isNull();
    }

    private AdminImageUploadService service(
            StorageService storage,
            int maxWidth,
            int maxHeight,
            long maxPixels,
            int outputMaxEdge
    ) {
        return new AdminImageUploadService(storage, new ImageUploadProperties(
                DataSize.ofMegabytes(8), maxWidth, maxHeight, maxPixels, outputMaxEdge));
    }

    private static byte[] image(String format, int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                graphics.setColor(new Color(0x33, 0x66, 0x99));
                graphics.fillRect(0, 0, width, height);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException(format + " writer unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] jpegWithOrientationSix() {
        byte[] jpeg = image("jpeg", 2, 1);
        byte[] exif = {
                (byte) 0xff, (byte) 0xe1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
                0x00, 0x06, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] result = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }

    private static byte[] vp8xHeaderOnly() {
        return new byte[] {
                'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', 'X', 10, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    }

    private void assertProblem(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, int status, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(status);
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }

    private static Path repositoryMedia(String relative) {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path rootMedia = workingDirectory.resolve("media").resolve(relative);
        if (Files.isRegularFile(rootMedia)) {
            return rootMedia;
        }
        Path moduleMedia = workingDirectory.resolve("../media").normalize().resolve(relative);
        if (Files.isRegularFile(moduleMedia)) {
            return moduleMedia;
        }
        throw new IllegalStateException("Repository media fixture not found: " + relative);
    }

    private static final class CapturingStorage implements StorageService {

        private byte[] content;

        @Override
        public Optional<StoredMedia> find(String storageKey) {
            return Optional.empty();
        }

        @Override
        public StoredAsset store(String storageKey, byte[] content) {
            this.content = content.clone();
            return new StoredAsset(storageKey, content.length);
        }
    }
}
