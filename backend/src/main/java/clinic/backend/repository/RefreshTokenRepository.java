package clinic.backend.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    /**
     * The single conditional update that serializes concurrent refreshes (docs/security.md).
     *
     * @return {@code 1} for exactly one caller per token, otherwise {@code 0}
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.usedAt = :now
             where t.tokenHash = :tokenHash and t.usedAt is null
            """)
    int markUsedIfUnused(@Param("tokenHash") byte[] tokenHash, @Param("now") Instant now);
}
