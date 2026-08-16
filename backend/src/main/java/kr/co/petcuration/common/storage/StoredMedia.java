package kr.co.petcuration.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record StoredMedia(
        Resource resource,
        MediaType contentType,
        long contentLength
) {
}
