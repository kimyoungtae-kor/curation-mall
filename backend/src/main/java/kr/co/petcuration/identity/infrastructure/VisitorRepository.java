package kr.co.petcuration.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorRepository extends JpaRepository<VisitorEntity, UUID> {

    Optional<VisitorEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VisitorEntity v where v.id = :id")
    Optional<VisitorEntity> findByIdForUpdate(@Param("id") UUID id);
}
