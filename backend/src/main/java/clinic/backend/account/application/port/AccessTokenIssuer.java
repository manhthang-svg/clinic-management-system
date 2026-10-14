package clinic.backend.account.application.port;

import java.time.Instant;

import clinic.backend.account.domain.StaffAccount;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(StaffAccount account, Instant now);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
