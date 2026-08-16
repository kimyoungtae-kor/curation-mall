package kr.co.petcuration.merchandising.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Map;
import java.util.UUID;
import kr.co.petcuration.merchandising.domain.HomeSectionKey;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "home_sections")
public class HomeSectionEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_key", nullable = false, unique = true, length = 40)
    private HomeSectionKey sectionKey;

    @Column(length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> content;

    @Column(name = "sort_order", nullable = false, unique = true)
    private int sortOrder;

    @Version
    @Column(nullable = false)
    private long version;

    protected HomeSectionEntity() {
    }

    public HomeSectionKey getSectionKey() {
        return sectionKey;
    }

    public Map<String, Object> getContent() {
        return content;
    }
}
