package kr.co.petcuration.common.storage;

import java.io.IOException;
import java.util.Optional;

public interface StorageService {

    Optional<StoredMedia> find(String storageKey);

    StoredAsset store(String storageKey, byte[] content) throws IOException;
}
