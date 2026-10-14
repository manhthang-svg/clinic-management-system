package clinic.backend.account.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.account.domain.SessionRevocationReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSessionEntity s
               set s.revokedAt = :now, s.revocationReason = :reason
             where s.id = :sessionId and s.revokedAt is null
            """)
    int revoke(
            @Param("sessionId") UUID sessionId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSessionEntity s
               set s.revokedAt = :now, s.revocationReason = :reason
             where s.staffAccountId = :accountId and s.id <> :keptSessionId and s.revokedAt is null
            """)
    int revokeAllForAccountExcept(
            @Param("accountId") UUID accountId,
            @Param("keptSessionId") UUID keptSessionId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now);

    // Refresh tokens are removed by ON DELETE CASCADE.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from AuthSessionEntity s
             where s.absoluteExpiresAt < :cutoff or s.revokedAt < :cutoff
            """)
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
