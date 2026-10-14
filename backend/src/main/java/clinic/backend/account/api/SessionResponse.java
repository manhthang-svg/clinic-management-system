package clinic.backend.account.api;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.account.application.SessionTokens;

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

    static SessionResponse from(SessionTokens tokens) {
        SessionTokens.AccountSummary account = tokens.account();
        return new SessionResponse(
                tokens.accessToken(),
                "Bearer",
                tokens.accessTokenExpiresAt(),
                tokens.passwordChangeRequired(),
                new AccountSummaryResponse(
                        account.id(), account.username(), account.fullName(), account.role().name()));
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
