package kr.co.petcuration.catalog.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.co.petcuration.catalog.domain.ProductStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Formula;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandEntity brand;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Formula("(select min(pv.price) from product_variants pv where pv.product_id = id and pv.status = 'ACTIVE')")
    private Long minimumActivePrice;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "product")
    @OrderBy("sortOrder ASC")
    private List<ProductVariantEntity> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @OrderBy("sortOrder ASC")
    private List<ProductImageEntity> images = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @OrderBy("sortOrder ASC")
    private Set<CategoryEntity> categories = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "product_species",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "species_id")
    )
    @OrderBy("sortOrder ASC")
    private Set<SpeciesEntity> species = new LinkedHashSet<>();

    protected ProductEntity() {
    }

    public UUID getId() {
        return id;
    }

    public BrandEntity getBrand() {
        return brand;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean isFeatured() {
        return featured;
    }

    public List<ProductVariantEntity> getVariants() {
        return variants;
    }

    public List<ProductImageEntity> getImages() {
        return images;
    }

    public Set<CategoryEntity> getCategories() {
        return categories;
    }

    public Set<SpeciesEntity> getSpecies() {
        return species;
    }
}
