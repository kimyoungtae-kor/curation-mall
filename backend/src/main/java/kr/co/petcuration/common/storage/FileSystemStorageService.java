package kr.co.petcuration.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

final class FileSystemStorageService implements StorageService {

    private final Path root;
    private final Path realRoot;

    FileSystemStorageService(StorageProperties properties) {
        root = properties.root().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Media storage root is not a directory: " + root);
        }

        try {
            realRoot = root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Media storage root cannot be resolved: " + root, exception);
        }
    }

    @Override
    public Optional<StoredMedia> find(String storageKey) {
        String safeKey = normalizeKey(storageKey);
        if (safeKey.isBlank()) {
            return Optional.empty();
        }

        Path candidate = root.resolve(safeKey).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }

        try {
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot) || !Files.isRegularFile(realCandidate)) {
                return Optional.empty();
            }

            FileSystemResource resource = new FileSystemResource(realCandidate);
            MediaType contentType = MediaTypeFactory
                    .getMediaType(realCandidate.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return Optional.of(new StoredMedia(resource, contentType, Files.size(realCandidate)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String normalizeKey(String storageKey) {
        if (storageKey == null || storageKey.indexOf('\0') >= 0) {
            return "";
        }

        String normalized = storageKey.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
