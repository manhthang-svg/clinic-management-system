package clinic.backend.service;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.model.Role;

/**
 * Tokens handed to the client after a successful login or refresh.
 *
 * @param sessionExpiresAt absolute session expiry; the refresh cookie must not outlive it
 */
public record SessionTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant sessionExpiresAt,
        boolean passwordChangeRequired,
        AccountSummary account) {

    public record AccountSummary(UUID id, String username, String fullName, Role role) {
    }

    @Override
    public String toString() {
        return "SessionTokens[account=" + account.id() + ", tokens redacted]";
    }
}
