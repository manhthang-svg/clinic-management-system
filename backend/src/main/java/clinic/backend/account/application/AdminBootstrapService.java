package clinic.backend.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import clinic.backend.account.application.port.PasswordHasher;
import clinic.backend.account.application.port.SecurityAuditLog;
import clinic.backend.account.application.port.SecurityAuditLog.EventType;
import clinic.backend.account.application.port.SecurityAuditLog.Outcome;
import clinic.backend.account.application.port.StaffAccountRepository;
import clinic.backend.account.domain.PasswordPolicy;
import clinic.backend.account.domain.Role;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.account.domain.Username;
import clinic.backend.shared.id.UuidV7Generator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Admin of a new environment from configuration (AQ-10).
 */
@Service
public class AdminBootstrapService {

    private final StaffAccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final SecurityAuditLog auditLog;
    private final UuidV7Generator ids;
    private final Clock clock;

    public AdminBootstrapService(
            StaffAccountRepository accounts,
            PasswordHasher passwordHasher,
            SecurityAuditLog auditLog,
            UuidV7Generator ids,
            Clock clock) {

        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.auditLog = auditLog;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * @return the created account, or empty when an Admin already exists
     * @throws IllegalStateException when the configured values are unusable
     */
    @Transactional
    public Optional<StaffAccount> createFirstAdminIfMissing(String username, String password, String fullName) {
        if (accounts.existsWithRole(Role.ADMIN)) {
            return Optional.empty();
        }

        Username adminUsername = Username.parse(username)
                .orElseThrow(() -> new IllegalStateException(
                        "The bootstrap Admin username does not match the username format"));
        if (PasswordPolicy.check(password).isPresent()) {
            throw new IllegalStateException("The bootstrap Admin password does not satisfy the password policy");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalStateException("The bootstrap Admin full name must not be blank");
        }

        Instant now = clock.instant();
        StaffAccount admin = accounts.save(StaffAccount.create(
                ids.next(),
                adminUsername,
                passwordHasher.hash(password),
                fullName.strip(),
                null,
                null,
                null,
                Role.ADMIN,
                now));

        auditLog.record(new SecurityAuditLog.Event(
                EventType.ACCOUNT_CREATED, null, admin.id(), Outcome.SUCCESS, now));
        return Optional.of(admin);
    }
}
