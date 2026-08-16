package kr.co.petcuration.cart.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.cart.domain.CartStatus;
import kr.co.petcuration.identity.application.OwnerIdentity;

@Entity
@Table(name = "carts")
public class CartEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "visitor_id")
    private UUID visitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<CartItemEntity> items = new ArrayList<>();

    protected CartEntity() {
    }

    public CartEntity(UUID id, OwnerIdentity owner, Instant now) {
        this.id = id;
        this.userId = owner.userId();
        this.visitorId = owner.visitorId();
        this.status = CartStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public CartItemEntity addItem(UUID itemId, UUID variantId, int quantity, long price, Instant now) {
        CartItemEntity item = new CartItemEntity(itemId, this, variantId, quantity, price, now);
        items.add(item);
        touch(now);
        return item;
    }

    public void removeItem(CartItemEntity item, Instant now) {
        items.remove(item);
        touch(now);
    }

    public void markMerged(Instant now) {
        status = CartStatus.MERGED;
        touch(now);
    }

    public void markExpired(Instant now) {
        status = CartStatus.EXPIRED;
        touch(now);
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public CartStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItemEntity> getItems() {
        return items;
    }
}
