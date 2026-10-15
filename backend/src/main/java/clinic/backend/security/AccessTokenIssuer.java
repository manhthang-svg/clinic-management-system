package clinic.backend.security;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.model.StaffAccount;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(StaffAccount account, UUID sessionId, Instant now);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
