package kr.co.petcuration.merchandising.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.merchandising.domain.PublicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID> {

    @Query(
            value = """
                    SELECT collection
                      FROM CollectionEntity collection
                     WHERE collection.status = :status
                       AND collection.publishedAt IS NOT NULL
                       AND collection.publishedAt <= :publicAt
                       AND (collection.expiresAt IS NULL OR collection.expiresAt > :publicAt)
                     ORDER BY collection.sortOrder ASC, collection.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(collection)
                      FROM CollectionEntity collection
                     WHERE collection.status = :status
                       AND collection.publishedAt IS NOT NULL
                       AND collection.publishedAt <= :publicAt
                       AND (collection.expiresAt IS NULL OR collection.expiresAt > :publicAt)
                    """
    )
    Page<CollectionEntity> findPublicAt(
            @Param("status") PublicationStatus status,
            @Param("publicAt") Instant publicAt,
            Pageable pageable
    );

    @Query("""
            SELECT collection
              FROM CollectionEntity collection
             WHERE collection.featured = TRUE
               AND collection.status = :status
               AND collection.publishedAt IS NOT NULL
               AND collection.publishedAt <= :publicAt
               AND (collection.expiresAt IS NULL OR collection.expiresAt > :publicAt)
             ORDER BY collection.sortOrder ASC, collection.id ASC
            """)
    Page<CollectionEntity> findFeaturedPublicAt(
            @Param("status") PublicationStatus status,
            @Param("publicAt") Instant publicAt,
            Pageable pageable
    );

    @Query("""
            SELECT collection
              FROM CollectionEntity collection
             WHERE collection.slug = :slug
               AND collection.status = :status
               AND collection.publishedAt IS NOT NULL
               AND collection.publishedAt <= :publicAt
               AND (collection.expiresAt IS NULL OR collection.expiresAt > :publicAt)
            """)
    Optional<CollectionEntity> findPublicBySlug(
            @Param("slug") String slug,
            @Param("status") PublicationStatus status,
            @Param("publicAt") Instant publicAt
    );
}
