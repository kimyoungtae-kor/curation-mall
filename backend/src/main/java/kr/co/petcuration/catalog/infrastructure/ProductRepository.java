package kr.co.petcuration.catalog.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.catalog.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findBySlugAndStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
            String slug,
            ProductStatus status,
            Instant publishedAtInclusive
    );
}
