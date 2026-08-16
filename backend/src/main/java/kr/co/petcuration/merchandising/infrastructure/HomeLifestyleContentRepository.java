package kr.co.petcuration.merchandising.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.merchandising.domain.HomeSectionKey;
import kr.co.petcuration.merchandising.domain.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomeLifestyleContentRepository extends JpaRepository<HomeLifestyleContentEntity, UUID> {

    @Query("""
            SELECT content
              FROM HomeLifestyleContentEntity content
             WHERE content.section.sectionKey = :sectionKey
               AND content.status = :status
               AND content.publishedAt IS NOT NULL
               AND content.publishedAt <= :publicAt
               AND (content.expiresAt IS NULL OR content.expiresAt > :publicAt)
             ORDER BY content.sortOrder ASC, content.id ASC
            """)
    List<HomeLifestyleContentEntity> findPublicAt(
            @Param("sectionKey") HomeSectionKey sectionKey,
            @Param("status") PublicationStatus status,
            @Param("publicAt") Instant publicAt
    );
}
