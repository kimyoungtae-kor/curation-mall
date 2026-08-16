package kr.co.petcuration.cart.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.cart.domain.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartEntity c where c.userId = :userId and c.status = :status")
    Optional<CartEntity> findUserCartForUpdate(@Param("userId") UUID userId, @Param("status") CartStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartEntity c where c.visitorId = :visitorId and c.status = :status")
    Optional<CartEntity> findVisitorCartForUpdate(@Param("visitorId") UUID visitorId, @Param("status") CartStatus status);

    @Query("select c from CartEntity c where c.userId = :userId and c.status = :status")
    Optional<CartEntity> findUserCart(@Param("userId") UUID userId, @Param("status") CartStatus status);

    @Query("select c from CartEntity c where c.visitorId = :visitorId and c.status = :status")
    Optional<CartEntity> findVisitorCart(@Param("visitorId") UUID visitorId, @Param("status") CartStatus status);
}
