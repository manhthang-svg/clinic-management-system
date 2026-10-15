package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Body returned by login and refresh. The refresh token is sent only as an HttpOnly cookie.
 */
public record SessionResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        boolean passwordChangeRequired,
        AccountSummaryResponse account) {

    public record AccountSummaryResponse(UUID id, String username, String fullName, String role) {
    }

    /**
     * Prevents MVC debug/trace logging from exposing the bearer token.
     */
    @Override
    public String toString() {
        return "SessionResponse[accessToken=redacted, tokenType=" + tokenType
                + ", expiresAt=" + expiresAt
                + ", passwordChangeRequired=" + passwordChangeRequired
                + ", account=" + account + "]";
    }
}
