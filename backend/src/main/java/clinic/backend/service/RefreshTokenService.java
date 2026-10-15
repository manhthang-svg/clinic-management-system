package clinic.backend.service;

import java.time.Instant;
import java.util.Optional;

import clinic.backend.entity.AuthSession;
import clinic.backend.entity.StaffAccount;

/**
 * Refresh tokens of login sessions: issue, rotation with reuse detection, revocation and cleanup
 * (docs/security.md, Refresh Tokens).
 */
public interface RefreshTokenService {

    /**
     * Issues a new refresh token for the session plus a fresh access token. Runs in the caller's transaction.
     */
    SessionTokens issueTokens(StaffAccount account, AuthSession session, Instant now);

    /**
     * Rotates a refresh token. Reusing a token that was already rotated revokes its whole session.
     *
     * @return the new tokens, or empty when the token is unknown, expired, reused, or its session or account
     *         can no longer be used
     */
    Optional<SessionTokens> rotate(String rawRefreshToken);

    /**
     * Ends only the session of the presented token (UC-2). Unknown, expired or already revoked tokens are
     * accepted silently.
     */
    void revoke(String rawRefreshToken);

    /**
     * Deletes sessions that ended longer ago than the retention period (docs/security.md, Cleanup).
     *
     * @return number of deleted sessions
     */
    int deleteEndedSessions();
}
