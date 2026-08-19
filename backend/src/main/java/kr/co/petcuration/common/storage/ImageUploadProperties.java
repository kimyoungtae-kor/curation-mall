package kr.co.petcuration.common.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.storage.image-upload")
public record ImageUploadProperties(
        @NotNull DataSize maxFileSize,
        @Min(1) int maxWidth,
        @Min(1) int maxHeight,
        @Min(1) long maxPixels,
        @Min(1) int outputMaxEdge
) {
}
