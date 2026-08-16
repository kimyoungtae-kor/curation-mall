package kr.co.petcuration.cart.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kr.co.petcuration.identity.application.OwnerIdentity;

@Entity
@Table(name = "wishlist_items")
public class WishlistItemEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "visitor_id")
    private UUID visitorId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WishlistItemEntity() {
    }

    public WishlistItemEntity(UUID id, OwnerIdentity owner, UUID productId, Instant now) {
        this.id = id;
        this.userId = owner.userId();
        this.visitorId = owner.visitorId();
        this.productId = productId;
        this.createdAt = now;
    }

    public UUID getProductId() {
        return productId;
    }
}
