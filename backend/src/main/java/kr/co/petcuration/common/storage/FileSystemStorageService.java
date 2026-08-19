package kr.co.petcuration.common.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

final class FileSystemStorageService implements StorageService {

    private static final Pattern SAFE_STORAGE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");

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

    @Override
    public StoredAsset store(String storageKey, byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Stored content must not be empty");
        }

        Path relativeKey = writableRelativeKey(storageKey);
        Path target = realRoot.resolve(relativeKey).normalize();
        if (!target.startsWith(realRoot) || target.getParent() == null) {
            throw new IllegalArgumentException("Storage key escapes the configured root");
        }

        Path parent = createSafeDirectories(realRoot.relativize(target.getParent()));
        target = parent.resolve(target.getFileName().toString());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(storageKey);
        }

        Path temporary = Files.createTempFile(parent, ".upload-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            Path verifiedParent = parent.toRealPath();
            if (!verifiedParent.equals(parent) || !verifiedParent.startsWith(realRoot)) {
                throw new IOException("Storage destination changed while writing");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(storageKey);
            }

            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            moved = true;
            return new StoredAsset(storageKey, Files.size(target));
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private Path writableRelativeKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\0') >= 0
                || storageKey.indexOf('\\') >= 0 || storageKey.startsWith("/")
                || storageKey.endsWith("/") || storageKey.contains("//")
                || !SAFE_STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }

        try {
            Path relative = Path.of(storageKey);
            if (relative.isAbsolute()) {
                throw new IllegalArgumentException("Storage key must be relative");
            }
            for (Path segment : relative) {
                String value = segment.toString();
                if (value.equals(".") || value.equals("..")) {
                    throw new IllegalArgumentException("Storage key contains traversal segments");
                }
            }
            return relative;
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid storage key", exception);
        }
    }

    private Path createSafeDirectories(Path relativeParent) throws IOException {
        Path current = realRoot;
        for (Path segment : relativeParent) {
            Path next = current.resolve(segment.toString());
            if (Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(next) || !Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Storage path contains a non-directory or symbolic link");
                }
            } else {
                try {
                    Files.createDirectory(next);
                } catch (FileAlreadyExistsException exception) {
                    // A concurrent upload can create the same YYYY/MM directory between the check and mkdir.
                    if (Files.isSymbolicLink(next) || !Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Storage path contains a non-directory or symbolic link", exception);
                    }
                }
            }

            Path resolved = next.toRealPath();
            if (!resolved.startsWith(realRoot)) {
                throw new IOException("Storage path escapes the configured root");
            }
            current = resolved;
        }
        return current;
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
