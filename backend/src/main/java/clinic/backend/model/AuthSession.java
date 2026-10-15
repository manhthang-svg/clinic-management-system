package clinic.backend.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.SessionRevocationReason;

/**
 * One login session: the family of refresh tokens created by a single successful login.
 *
 * <p>The absolute expiry is fixed at login and is never extended by rotation.
 */
public record AuthSession(
        UUID id,
        UUID accountId,
        Instant createdAt,
        Instant absoluteExpiresAt,
        Instant revokedAt,
        SessionRevocationReason revocationReason) {

    public AuthSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
        if ((revokedAt == null) != (revocationReason == null)) {
            throw new IllegalArgumentException("revokedAt and revocationReason must be set together");
        }
    }

    public static AuthSession start(UUID id, UUID accountId, Instant now, Duration absoluteLifetime) {
        return new AuthSession(id, accountId, now, now.plus(absoluteLifetime), null, null);
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(absoluteExpiresAt);
    }

    /**
     * A refresh token expires after the idle timeout, but never after the session itself.
     */
    public Instant refreshTokenExpiry(Instant issuedAt, Duration idleTimeout) {
        Instant idleExpiry = issuedAt.plus(idleTimeout);
        return idleExpiry.isBefore(absoluteExpiresAt) ? idleExpiry : absoluteExpiresAt;
    }
}
