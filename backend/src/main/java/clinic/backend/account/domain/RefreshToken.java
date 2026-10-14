package clinic.backend.account.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * A refresh token of a session. Only the SHA-256 digest of the raw value is kept.
 */
public record RefreshToken(
        UUID id,
        UUID sessionId,
        byte[] tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt) {

    public RefreshToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        tokenHash = tokenHash.clone();
    }

    public static RefreshToken issue(UUID id, UUID sessionId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
        return new RefreshToken(id, sessionId, tokenHash, issuedAt, expiresAt, null);
    }

    @Override
    public byte[] tokenHash() {
        return tokenHash.clone();
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RefreshToken token
                && id.equals(token.id)
                && sessionId.equals(token.sessionId)
                && Arrays.equals(tokenHash, token.tokenHash)
                && issuedAt.equals(token.issuedAt)
                && expiresAt.equals(token.expiresAt)
                && Objects.equals(usedAt, token.usedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sessionId, Arrays.hashCode(tokenHash), issuedAt, expiresAt, usedAt);
    }

    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", sessionId=" + sessionId + "]";
    }
}
