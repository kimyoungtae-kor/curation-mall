package kr.co.petcuration.cart.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_at_add", nullable = false)
    private long unitPriceAtAdd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CartItemEntity() {
    }

    CartItemEntity(UUID id, CartEntity cart, UUID variantId, int quantity, long unitPriceAtAdd, Instant now) {
        this.id = id;
        this.cart = cart;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitPriceAtAdd = unitPriceAtAdd;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeQuantity(int quantity, Instant now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceAtAdd() {
        return unitPriceAtAdd;
    }
}
