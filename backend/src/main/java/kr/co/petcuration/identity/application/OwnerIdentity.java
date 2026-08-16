package kr.co.petcuration.identity.application;

import java.util.Objects;
import java.util.UUID;

public record OwnerIdentity(UUID userId, UUID visitorId) {

    public OwnerIdentity {
        if ((userId == null) == (visitorId == null)) {
            throw new IllegalArgumentException("Exactly one owner identifier is required.");
        }
    }

    public static OwnerIdentity member(UUID userId) {
        return new OwnerIdentity(Objects.requireNonNull(userId), null);
    }

    public static OwnerIdentity visitor(UUID visitorId) {
        return new OwnerIdentity(null, Objects.requireNonNull(visitorId));
    }

    public boolean isMember() {
        return userId != null;
    }
}
