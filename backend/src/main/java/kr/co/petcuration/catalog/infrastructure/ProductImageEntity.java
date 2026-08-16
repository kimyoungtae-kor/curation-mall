package kr.co.petcuration.catalog.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "product_images")
public class ProductImageEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "alt_text", nullable = false, length = 300)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductImageEntity() {
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getAltText() {
        return altText;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

