package kr.co.petcuration.common.storage;

import java.util.Optional;

public interface StorageService {

    Optional<StoredMedia> find(String storageKey);
}
