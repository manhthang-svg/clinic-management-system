package clinic.backend.repository;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.entity.AuthSession;
import clinic.backend.enums.SessionRevocationReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /**
     * Revokes the session if it is not revoked yet.
     *
     * @return {@code 1} when this call revoked it, otherwise {@code 0}
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
               set s.revokedAt = :now, s.revocationReason = :reason
             where s.id = :sessionId and s.revokedAt is null
            """)
    int revoke(
            @Param("sessionId") UUID sessionId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now);

    /**
     * Revokes every active session of the account except one.
     *
     * @return number of revoked sessions
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
               set s.revokedAt = :now, s.revocationReason = :reason
             where s.staffAccountId = :accountId and s.id <> :keptSessionId and s.revokedAt is null
            """)
    int revokeAllForAccountExcept(
            @Param("accountId") UUID accountId,
            @Param("keptSessionId") UUID keptSessionId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now);

    /** Revokes every active session of an account after an operator Admin recovery. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
               set s.revokedAt = :now, s.revocationReason = :reason
             where s.staffAccountId = :accountId and s.revokedAt is null
            """)
    int revokeAllForAccount(
            @Param("accountId") UUID accountId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now);

    /**
     * Deletes sessions that expired or were revoked before the cutoff. Refresh tokens are removed by
     * ON DELETE CASCADE.
     *
     * @return number of deleted sessions
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from AuthSession s
             where s.absoluteExpiresAt < :cutoff or s.revokedAt < :cutoff
            """)
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
