package kr.co.petcuration.common.storage;

public record StoredAsset(
        String storageKey,
        long contentLength
) {
}
