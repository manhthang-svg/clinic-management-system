package clinic.backend.account.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(byte[] tokenHash);

    /**
     * The single conditional update that serializes concurrent refreshes (docs/security.md).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.usedAt = :now
             where t.tokenHash = :tokenHash and t.usedAt is null
            """)
    int markUsedIfUnused(@Param("tokenHash") byte[] tokenHash, @Param("now") Instant now);
}
