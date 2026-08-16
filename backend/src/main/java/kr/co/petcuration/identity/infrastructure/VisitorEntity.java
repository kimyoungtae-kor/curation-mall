package kr.co.petcuration.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visitors")
public class VisitorEntity {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VisitorEntity() {
    }

    public VisitorEntity(UUID id, String tokenHash, Instant expiresAt, Instant now) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.lastSeenAt = now;
        this.createdAt = now;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void touch(Instant now, Instant renewedExpiry) {
        this.lastSeenAt = now;
        this.expiresAt = renewedExpiry;
    }

    public UUID getId() {
        return id;
    }
}
