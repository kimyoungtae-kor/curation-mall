package kr.co.petcuration.merchandising.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "collection_products")
public class CollectionProductEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private CollectionEntity collection;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CollectionProductEntity() {
    }

    public UUID getProductId() {
        return productId;
    }
}
