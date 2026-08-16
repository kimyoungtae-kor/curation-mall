package kr.co.petcuration.merchandising.infrastructure;

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
import kr.co.petcuration.merchandising.domain.PublicationStatus;

@Entity
@Table(name = "collections")
public class CollectionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "hero_storage_key", nullable = false, length = 500)
    private String heroStorageKey;

    @Column(name = "hero_alt_text", nullable = false, length = 300)
    private String heroAltText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "collection")
    @OrderBy("sortOrder ASC")
    private List<CollectionProductEntity> productLinks = new ArrayList<>();

    protected CollectionEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getHeroStorageKey() {
        return heroStorageKey;
    }

    public String getHeroAltText() {
        return heroAltText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public List<CollectionProductEntity> getProductLinks() {
        return productLinks;
    }
}
