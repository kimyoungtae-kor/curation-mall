package kr.co.petcuration.catalog.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import kr.co.petcuration.catalog.domain.CatalogReferenceStatus;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogReferenceStatus status;

    protected CategoryEntity() {
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }
}
