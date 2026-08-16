package kr.co.petcuration.merchandising.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import kr.co.petcuration.merchandising.domain.MerchandisingLinkType;
import kr.co.petcuration.merchandising.domain.PublicationStatus;

@Entity
@Table(name = "home_lifestyle_contents")
public class HomeLifestyleContentEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private HomeSectionEntity section;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "image_storage_key", nullable = false, length = 500)
    private String imageStorageKey;

    @Column(name = "image_alt_text", nullable = false, length = 300)
    private String imageAltText;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private MerchandisingLinkType linkType;

    @Column(name = "link_value", nullable = false, length = 200)
    private String linkValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected HomeLifestyleContentEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageStorageKey() {
        return imageStorageKey;
    }

    public String getImageAltText() {
        return imageAltText;
    }

    public MerchandisingLinkType getLinkType() {
        return linkType;
    }

    public String getLinkValue() {
        return linkValue;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
