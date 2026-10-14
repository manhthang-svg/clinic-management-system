package clinic.backend.account.application.port;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.account.domain.StaffAccount;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(StaffAccount account, UUID sessionId, Instant now);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
