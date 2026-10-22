package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body returned by login and refresh. The refresh token is sent only as an HttpOnly cookie.
 */
public record SessionResponse(
        @Schema(description = "Bearer access token. Keep it in memory; never persist it in browser storage.",
                format = "jwt", accessMode = Schema.AccessMode.READ_ONLY)
        String accessToken,
        @Schema(example = "Bearer")
        String tokenType,
        @Schema(description = "Access-token expiry in UTC.", example = "2026-10-22T13:15:00Z")
        Instant expiresAt,
        @Schema(description = "When true, only viewing own profile, changing password and logout are allowed.")
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
