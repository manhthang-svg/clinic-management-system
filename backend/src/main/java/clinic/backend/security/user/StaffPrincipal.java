package clinic.backend.security.user;

import java.util.UUID;

import clinic.backend.security.jwt.AccessTokenClaims;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated staff member of a request, read from a validated access token.
 *
 * @param accountId the staff account (token subject)
 * @param sessionId the login session the token belongs to
 */
public record StaffPrincipal(UUID accountId, UUID sessionId) {

    public static StaffPrincipal from(Jwt jwt) {
        return new StaffPrincipal(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(AccessTokenClaims.SESSION_ID)));
    }
}
