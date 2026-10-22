package clinic.backend.service;

import java.util.UUID;

/** Result shown once to the operator after the Admin recovery procedure defined in docs/security.md. */
public record AdminRecoveryResult(UUID accountId, String username, String temporaryPassword) {

    @Override
    public String toString() {
        return "AdminRecoveryResult[accountId=" + accountId + ", username=" + username
                + ", temporaryPassword=redacted]";
    }
}
