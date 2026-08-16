package kr.co.petcuration.order.application;

import java.util.UUID;

public record CommerceActor(UUID userId, UUID visitorId, boolean admin) {

    public static CommerceActor member(UUID userId) {
        return member(userId, false);
    }

    public static CommerceActor member(UUID userId, boolean admin) {
        return new CommerceActor(userId, null, admin);
    }

    public static CommerceActor visitor(UUID visitorId) {
        return new CommerceActor(null, visitorId, false);
    }

    public boolean isMember() {
        return userId != null;
    }
}
