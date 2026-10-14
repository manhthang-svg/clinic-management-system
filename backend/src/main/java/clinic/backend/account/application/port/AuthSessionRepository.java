package clinic.backend.account.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.account.domain.AuthSession;
import clinic.backend.account.domain.RefreshToken;
import clinic.backend.account.domain.SessionRevocationReason;

public interface AuthSessionRepository {

    void saveSession(AuthSession session);

    Optional<AuthSession> findSession(UUID sessionId);

    void saveToken(RefreshToken token);

    Optional<RefreshToken> findTokenByHash(byte[] tokenHash);

    /**
     * Atomically marks the token as used if it has not been used yet.
     *
     * @return {@code true} for exactly one caller per token
     */
    boolean markTokenUsedIfUnused(byte[] tokenHash, Instant now);

    /**
     * Revokes the session if it is not revoked yet.
     *
     * @return {@code true} when this call revoked it
     */
    boolean revokeSession(UUID sessionId, SessionRevocationReason reason, Instant now);

    /**
     * Revokes every active session of the account except one.
     *
     * @return number of revoked sessions
     */
    int revokeAllForAccountExcept(UUID accountId, UUID keptSessionId, SessionRevocationReason reason, Instant now);

    /**
     * Deletes sessions, with their tokens, that expired or were revoked before the cutoff.
     *
     * @return number of deleted sessions
     */
    int deleteSessionsEndedBefore(Instant cutoff);
}
