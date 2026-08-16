package kr.co.petcuration.catalog.infrastructure;

import java.util.List;
import java.util.UUID;
import kr.co.petcuration.catalog.domain.CatalogReferenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByStatusOrderBySortOrderAscIdAsc(CatalogReferenceStatus status);
}
