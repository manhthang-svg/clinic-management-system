package clinic.backend.account.application.port;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.account.domain.Role;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.account.domain.Username;

public interface StaffAccountRepository {

    Optional<StaffAccount> findById(UUID id);

    Optional<StaffAccount> findByUsername(Username username);

    /**
     * Finds and locks an account for the duration of an authentication transaction.
     *
     * <p>The lock serializes password-attempt counter updates for the same account so concurrent
     * failures cannot be lost.
     */
    Optional<StaffAccount> findByUsernameForAuthentication(Username username);

    boolean existsWithRole(Role role);

    StaffAccount save(StaffAccount account);
}
