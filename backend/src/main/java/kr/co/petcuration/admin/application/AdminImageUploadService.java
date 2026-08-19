package kr.co.petcuration.admin.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import kr.co.petcuration.admin.api.AdminApiModels.MediaUpload;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.common.storage.ImageUploadProperties;
import kr.co.petcuration.common.storage.StorageService;
import kr.co.petcuration.common.storage.StoredAsset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminImageUploadService {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final Set<String> GENERIC_CONTENT_TYPES = Set.of("", "application/octet-stream");

    private final StorageService storageService;
    private final ImageUploadProperties properties;

    public AdminImageUploadService(StorageService storageService, ImageUploadProperties properties) {
        this.storageService = storageService;
        this.properties = properties;
    }

    public MediaUpload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw problem(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "이미지 파일이 필요합니다.",
                    "업로드할 이미지 파일을 선택해 주세요.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw fileTooLarge();
        }

        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException exception) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "이미지를 읽을 수 없습니다.",
                    "이미지 파일을 다시 선택해 주세요.");
        }
        if (source.length == 0) {
            throw problem(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "이미지 파일이 필요합니다.",
                    "빈 파일은 업로드할 수 없습니다.");
        }
        if (source.length > properties.maxFileSize().toBytes()) {
            throw fileTooLarge();
        }

        ProcessedImage processed = process(source, file.getContentType());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String prefix = "uploads/products/%04d/%02d/".formatted(today.getYear(), today.getMonthValue());

        for (int attempt = 0; attempt < 3; attempt++) {
            String storageKey = prefix + UUID.randomUUID() + "." + processed.format().extension;
            try {
                StoredAsset stored = storageService.store(storageKey, processed.content());
                return new MediaUpload(
                        stored.storageKey(),
                        "/media/" + stored.storageKey(),
                        processed.format().mediaType,
                        stored.contentLength(),
                        processed.width(),
                        processed.height()
                );
            } catch (FileAlreadyExistsException exception) {
                // A UUID collision is extraordinarily unlikely, but retrying keeps keys immutable without overwrites.
            } catch (IOException exception) {
                throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_FAILED", "이미지를 저장하지 못했습니다.",
                        "잠시 후 다시 시도해 주세요.");
            }
        }

        throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_FAILED", "이미지를 저장하지 못했습니다.",
                "새 이미지 저장 경로를 만들지 못했습니다.");
    }

    private ProcessedImage process(byte[] source, String declaredContentType) {
        ImageFormat format = detectFormat(source);
        validateDeclaredContentType(format, declaredContentType);
        if (format == ImageFormat.WEBP) {
            Dimensions dimensions = readWebpDimensions(source);
            validateDimensions(dimensions);
            // The JDK has no built-in, production-grade WebP codec. Preserve validated WebP bytes rather than
            // silently transcoding with an unmaintained dependency. Metadata-bearing WebP files are rejected by
            // readWebpDimensions so EXIF/GPS data is never exposed; JPEG and PNG are re-encoded below.
            return new ProcessedImage(source, format, dimensions.width(), dimensions.height());
        }

        return processJdkImage(source, format);
    }

    private ProcessedImage processJdkImage(byte[] source, ImageFormat format) {
        ImageReader reader = null;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            reader = readers.next();
            reader.setInput(input, true, true);
            Dimensions sourceDimensions = new Dimensions(reader.getWidth(0), reader.getHeight(0));
            validateDimensions(sourceDimensions);
            BufferedImage decoded = reader.read(0);
            if (decoded == null || decoded.getWidth() != sourceDimensions.width()
                    || decoded.getHeight() != sourceDimensions.height()) {
                throw invalidImage();
            }

            int orientation = format == ImageFormat.JPEG ? readJpegExifOrientation(source) : 1;
            BufferedImage normalized = renderNormalized(decoded, orientation, format);
            byte[] encoded = encode(normalized, format);
            return new ProcessedImage(encoded, format, normalized.getWidth(), normalized.getHeight());
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidImage();
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private BufferedImage renderNormalized(BufferedImage source, int orientation, ImageFormat format) {
        boolean swapsAxes = orientation >= 5 && orientation <= 8;
        int orientedWidth = swapsAxes ? source.getHeight() : source.getWidth();
        int orientedHeight = swapsAxes ? source.getWidth() : source.getHeight();
        double scale = Math.min(1.0,
                properties.outputMaxEdge() / (double) Math.max(orientedWidth, orientedHeight));
        int outputWidth = Math.max(1, (int) Math.round(orientedWidth * scale));
        int outputHeight = Math.max(1, (int) Math.round(orientedHeight * scale));
        int imageType = format == ImageFormat.PNG && source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage output = new BufferedImage(outputWidth, outputHeight, imageType);

        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            AffineTransform transform = AffineTransform.getScaleInstance(
                    outputWidth / (double) orientedWidth,
                    outputHeight / (double) orientedHeight
            );
            transform.concatenate(orientationTransform(orientation, source.getWidth(), source.getHeight()));
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private AffineTransform orientationTransform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private byte[] encode(BufferedImage image, ImageFormat format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (format == ImageFormat.PNG) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG writer is unavailable");
            }
            return output.toByteArray();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer is unavailable");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(0.9f);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private ImageFormat detectFormat(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return ImageFormat.PNG;
        }
        if (content.length >= 3 && unsigned(content[0]) == 0xff && unsigned(content[1]) == 0xd8
                && unsigned(content[2]) == 0xff) {
            return ImageFormat.JPEG;
        }
        if (content.length >= 20 && asciiEquals(content, 0, "RIFF") && asciiEquals(content, 8, "WEBP")) {
            return ImageFormat.WEBP;
        }
        throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT",
                "지원하지 않는 이미지 형식입니다.", "JPEG, PNG 또는 WebP 이미지만 업로드할 수 있습니다.");
    }

    private void validateDeclaredContentType(ImageFormat format, String declaredContentType) {
        String normalized = declaredContentType == null
                ? ""
                : declaredContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (GENERIC_CONTENT_TYPES.contains(normalized)) {
            return;
        }
        boolean matches = normalized.equals(format.mediaType)
                || (format == ImageFormat.JPEG && normalized.equals("image/jpg"));
        if (!matches) {
            throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMAGE_CONTENT_TYPE_MISMATCH",
                    "이미지 형식이 일치하지 않습니다.", "파일 내용과 브라우저가 전달한 이미지 형식이 다릅니다.");
        }
    }

    private Dimensions readWebpDimensions(byte[] content) {
        long declaredRiffSize = unsignedIntLittleEndian(content, 4);
        if (declaredRiffSize != content.length - 8L) {
            throw invalidImage();
        }

        Dimensions canvasDimensions = null;
        Dimensions payloadDimensions = null;
        int payloadCount = 0;
        int offset = 12;
        while (offset + 8 <= content.length) {
            String chunk = ascii(content, offset, 4);
            long chunkLength = unsignedIntLittleEndian(content, offset + 4);
            long dataStart = offset + 8L;
            long dataEnd = dataStart + chunkLength;
            if (chunkLength > Integer.MAX_VALUE || dataEnd > content.length) {
                throw invalidImage();
            }
            int start = (int) dataStart;
            int length = (int) chunkLength;

            if (chunk.equals("VP8X")) {
                if (offset != 12 || length != 10 || canvasDimensions != null) {
                    throw invalidImage();
                }
                int flags = unsigned(content[start]);
                if ((flags & 0x02) != 0) {
                    throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT",
                            "움직이는 이미지는 지원하지 않습니다.", "정지 WebP 이미지를 선택해 주세요.");
                }
                if ((flags & 0xed) != 0) {
                    throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT",
                            "메타데이터가 포함된 WebP는 지원하지 않습니다.",
                            "개인정보 보호를 위해 메타데이터를 제거한 WebP 또는 JPEG, PNG를 선택해 주세요.");
                }
                canvasDimensions = new Dimensions(
                        1 + unsigned24LittleEndian(content, start + 4),
                        1 + unsigned24LittleEndian(content, start + 7)
                );
            } else if (chunk.equals("VP8 ")) {
                if (++payloadCount != 1 || length < 10 || (unsigned(content[start]) & 0x01) != 0
                        || unsigned(content[start + 3]) != 0x9d
                        || unsigned(content[start + 4]) != 0x01 || unsigned(content[start + 5]) != 0x2a) {
                    throw invalidImage();
                }
                payloadDimensions = new Dimensions(
                        littleEndianUnsignedShort(content, start + 6) & 0x3fff,
                        littleEndianUnsignedShort(content, start + 8) & 0x3fff
                );
            } else if (chunk.equals("VP8L")) {
                if (++payloadCount != 1 || length < 5 || unsigned(content[start]) != 0x2f) {
                    throw invalidImage();
                }
                int b1 = unsigned(content[start + 1]);
                int b2 = unsigned(content[start + 2]);
                int b3 = unsigned(content[start + 3]);
                int b4 = unsigned(content[start + 4]);
                if ((b4 & 0xe0) != 0) {
                    throw invalidImage();
                }
                payloadDimensions = new Dimensions(
                        1 + b1 + ((b2 & 0x3f) << 8),
                        1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10)
                );
            } else if (chunk.equals("EXIF") || chunk.equals("XMP ") || chunk.equals("ICCP")) {
                throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT",
                        "메타데이터가 포함된 WebP는 지원하지 않습니다.",
                        "개인정보 보호를 위해 메타데이터를 제거한 WebP 또는 JPEG, PNG를 선택해 주세요.");
            } else if (chunk.equals("ANIM") || chunk.equals("ANMF")) {
                throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT",
                        "움직이는 이미지는 지원하지 않습니다.", "정지 WebP 이미지를 선택해 주세요.");
            } else if (!chunk.equals("ALPH")) {
                throw invalidImage();
            }

            long paddedEnd = dataEnd + (chunkLength & 1L);
            if (paddedEnd > content.length) {
                throw invalidImage();
            }
            offset = (int) paddedEnd;
        }
        if (offset != content.length || payloadCount != 1 || payloadDimensions == null) {
            throw invalidImage();
        }
        if (canvasDimensions != null && !canvasDimensions.equals(payloadDimensions)) {
            throw invalidImage();
        }
        return payloadDimensions;
    }

    private void validateDimensions(Dimensions dimensions) {
        int width = dimensions.width();
        int height = dimensions.height();
        long pixels = (long) width * height;
        if (width < 1 || height < 1 || width > properties.maxWidth()
                || height > properties.maxHeight() || pixels > properties.maxPixels()) {
            throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_DIMENSIONS_EXCEEDED",
                    "이미지 해상도가 너무 큽니다.", "더 작은 해상도의 이미지를 선택해 주세요.");
        }
    }

    private int readJpegExifOrientation(byte[] content) {
        int offset = 2;
        while (offset + 4 <= content.length) {
            if (unsigned(content[offset]) != 0xff) {
                return 1;
            }
            int marker = unsigned(content[offset + 1]);
            if (marker == 0xda || marker == 0xd9) {
                return 1;
            }
            if (marker == 0x00 || marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) {
                offset += 2;
                continue;
            }
            int segmentLength = bigEndianUnsignedShort(content, offset + 2);
            if (segmentLength < 2 || offset + 2L + segmentLength > content.length) {
                return 1;
            }
            int payload = offset + 4;
            int payloadLength = segmentLength - 2;
            if (marker == 0xe1 && payloadLength >= 14 && asciiEquals(content, payload, "Exif")
                    && content[payload + 4] == 0 && content[payload + 5] == 0) {
                return readTiffOrientation(content, payload + 6, payloadLength - 6);
            }
            offset += segmentLength + 2;
        }
        return 1;
    }

    private int readTiffOrientation(byte[] content, int start, int length) {
        if (length < 8) {
            return 1;
        }
        boolean littleEndian;
        if (content[start] == 'I' && content[start + 1] == 'I') {
            littleEndian = true;
        } else if (content[start] == 'M' && content[start + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        if (readUnsignedShort(content, start + 2, littleEndian) != 42) {
            return 1;
        }
        long directoryOffset = readUnsignedInt(content, start + 4, littleEndian);
        long directory = start + directoryOffset;
        long limit = start + (long) length;
        if (directoryOffset < 8 || directory + 2 > limit) {
            return 1;
        }
        int entries = readUnsignedShort(content, (int) directory, littleEndian);
        long entryOffset = directory + 2;
        for (int index = 0; index < entries; index++, entryOffset += 12) {
            if (entryOffset + 12 > limit) {
                return 1;
            }
            int entry = (int) entryOffset;
            int tag = readUnsignedShort(content, entry, littleEndian);
            int type = readUnsignedShort(content, entry + 2, littleEndian);
            long count = readUnsignedInt(content, entry + 4, littleEndian);
            if (tag == 0x0112 && type == 3 && count == 1) {
                int orientation = readUnsignedShort(content, entry + 8, littleEndian);
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
        }
        return 1;
    }

    private ApiException fileTooLarge() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_FILE_TOO_LARGE", "이미지 파일이 너무 큽니다.",
                "이미지는 한 장당 최대 " + properties.maxFileSize().toMegabytes() + "MB까지 업로드할 수 있습니다.");
    }

    private ApiException invalidImage() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "올바른 이미지 파일이 아닙니다.",
                "손상되지 않은 JPEG, PNG 또는 WebP 이미지를 선택해 주세요.");
    }

    private ApiException problem(HttpStatus status, String code, String title, String detail) {
        return new ApiException(status, code, title, detail);
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] content, int offset, String value) {
        if (offset < 0 || offset + value.length() > content.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (unsigned(content[offset + index]) != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private String ascii(byte[] content, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > content.length) {
            return "";
        }
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append((char) unsigned(content[offset + index]));
        }
        return result.toString();
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private int littleEndianUnsignedShort(byte[] content, int offset) {
        return unsigned(content[offset]) | unsigned(content[offset + 1]) << 8;
    }

    private int bigEndianUnsignedShort(byte[] content, int offset) {
        return unsigned(content[offset]) << 8 | unsigned(content[offset + 1]);
    }

    private long unsignedIntLittleEndian(byte[] content, int offset) {
        return unsigned(content[offset])
                | (long) unsigned(content[offset + 1]) << 8
                | (long) unsigned(content[offset + 2]) << 16
                | (long) unsigned(content[offset + 3]) << 24;
    }

    private int unsigned24LittleEndian(byte[] content, int offset) {
        return unsigned(content[offset]) | unsigned(content[offset + 1]) << 8 | unsigned(content[offset + 2]) << 16;
    }

    private int readUnsignedShort(byte[] content, int offset, boolean littleEndian) {
        return littleEndian ? littleEndianUnsignedShort(content, offset) : bigEndianUnsignedShort(content, offset);
    }

    private long readUnsignedInt(byte[] content, int offset, boolean littleEndian) {
        if (littleEndian) {
            return unsignedIntLittleEndian(content, offset);
        }
        return (long) unsigned(content[offset]) << 24
                | (long) unsigned(content[offset + 1]) << 16
                | (long) unsigned(content[offset + 2]) << 8
                | unsigned(content[offset + 3]);
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String mediaType;
        private final String extension;

        ImageFormat(String mediaType, String extension) {
            this.mediaType = mediaType;
            this.extension = extension;
        }
    }

    private record Dimensions(int width, int height) {
    }

    private record ProcessedImage(byte[] content, ImageFormat format, int width, int height) {
    }
}
