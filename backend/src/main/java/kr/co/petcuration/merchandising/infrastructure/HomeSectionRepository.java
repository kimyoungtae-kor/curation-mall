package kr.co.petcuration.merchandising.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeSectionRepository extends JpaRepository<HomeSectionEntity, UUID> {

    List<HomeSectionEntity> findAllByOrderBySortOrderAsc();
}
