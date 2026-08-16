package kr.co.petcuration.merchandising.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.merchandising.domain.HomeSectionKey;
import kr.co.petcuration.merchandising.domain.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomeHeroSlideRepository extends JpaRepository<HomeHeroSlideEntity, UUID> {

    @Query("""
            SELECT slide
              FROM HomeHeroSlideEntity slide
             WHERE slide.section.sectionKey = :sectionKey
               AND slide.status = :status
               AND slide.publishedAt IS NOT NULL
               AND slide.publishedAt <= :publicAt
               AND (slide.expiresAt IS NULL OR slide.expiresAt > :publicAt)
             ORDER BY slide.sortOrder ASC, slide.id ASC
            """)
    List<HomeHeroSlideEntity> findPublicAt(
            @Param("sectionKey") HomeSectionKey sectionKey,
            @Param("status") PublicationStatus status,
            @Param("publicAt") Instant publicAt
    );
}
