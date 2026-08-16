package kr.co.petcuration.cart.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, UUID> {

    Page<WishlistItemEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<WishlistItemEntity> findByVisitorIdOrderByCreatedAtDesc(UUID visitorId, Pageable pageable);

    List<WishlistItemEntity> findByVisitorId(UUID visitorId);

    Optional<WishlistItemEntity> findByUserIdAndProductId(UUID userId, UUID productId);

    Optional<WishlistItemEntity> findByVisitorIdAndProductId(UUID visitorId, UUID productId);

    long countByUserId(UUID userId);

    long countByVisitorId(UUID visitorId);

    void deleteByVisitorId(UUID visitorId);
}
