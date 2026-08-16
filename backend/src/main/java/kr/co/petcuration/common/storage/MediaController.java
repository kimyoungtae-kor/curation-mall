package kr.co.petcuration.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {

    private final StorageService storageService;
    private final CacheControl cacheControl;

    MediaController(StorageService storageService, StorageProperties properties) {
        this.storageService = storageService;
        cacheControl = CacheControl.maxAge(properties.publicCacheMaxAge()).cachePublic();
    }

    @GetMapping("/media/{*storageKey}")
    ResponseEntity<Resource> media(@PathVariable String storageKey) {
        return storageService.find(storageKey)
                .map(stored -> ResponseEntity.ok()
                        .cacheControl(cacheControl)
                        .contentType(stored.contentType())
                        .contentLength(stored.contentLength())
                        .body(stored.resource()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
