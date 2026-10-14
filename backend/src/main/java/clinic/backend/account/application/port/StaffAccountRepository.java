package clinic.backend.account.application.port;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.account.domain.Role;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.account.domain.Username;

public interface StaffAccountRepository {

    Optional<StaffAccount> findById(UUID id);

    Optional<StaffAccount> findByUsername(Username username);

    boolean existsWithRole(Role role);

    StaffAccount save(StaffAccount account);
}
