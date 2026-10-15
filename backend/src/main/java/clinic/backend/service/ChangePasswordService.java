package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.model.PasswordPolicy;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.model.StaffAccount;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.repository.SecurityAuditLog.Outcome;
import clinic.backend.repository.SecurityAuditLog;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.service.ChangePasswordResult.AccountUnavailable;
import clinic.backend.service.ChangePasswordResult.Changed;
import clinic.backend.service.ChangePasswordResult.CurrentPasswordIncorrect;
import clinic.backend.service.ChangePasswordResult.PolicyViolated;
import clinic.backend.service.ChangePasswordResult.Violation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-3 Change Password (docs/use-cases/UC-3-change-password.md).
 */
@Service
public class ChangePasswordService {

    private final StaffAccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecurityAuditLog auditLog;
    private final Clock clock;

    public ChangePasswordService(
            StaffAccountRepository accounts,
            AuthSessionRepository sessions,
            PasswordHasher passwordHasher,
            SecurityAuditLog auditLog,
            Clock clock) {

        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * @param accountId        the authenticated caller, taken from the token
     * @param currentSessionId the caller's session, which stays valid
     */
    @Transactional
    public ChangePasswordResult changePassword(
            UUID accountId, UUID currentSessionId, String currentPassword, String newPassword) {

        Instant now = clock.instant();
        Optional<StaffAccount> candidate = accounts.findById(accountId);
        if (candidate.isEmpty() || !candidate.get().canAuthenticate()) {
            return new AccountUnavailable();
        }
        StaffAccount account = candidate.get();

        // ASSUMPTION (UC-3): wrong current passwords count toward the login throttle.
        if (account.isLoginBlocked(now)) {
            passwordHasher.simulateMatch(currentPassword);
            return new CurrentPasswordIncorrect();
        }
        if (!passwordHasher.matches(currentPassword, account.passwordHash())) {
            if (account.registerFailedLogin(now)) {
                auditLog.record(new SecurityAuditLog.Event(
                        EventType.LOGIN_THROTTLED, accountId, accountId, Outcome.FAILURE, now));
            }
            accounts.save(account);
            return new CurrentPasswordIncorrect();
        }

        Optional<PasswordPolicy.Violation> violation = PasswordPolicy.check(newPassword);
        if (violation.isPresent()) {
            return new PolicyViolated(Violation.from(violation.get()));
        }
        if (newPassword.equals(currentPassword)) {
            return new PolicyViolated(Violation.SAME_AS_CURRENT);
        }

        account.clearFailedLoginAttempts(now);
        account.changePassword(passwordHasher.hash(newPassword), now);
        accounts.save(account);
        sessions.revokeAllForAccountExcept(accountId, currentSessionId, SessionRevocationReason.PASSWORD_CHANGED, now);
        auditLog.record(new SecurityAuditLog.Event(
                EventType.PASSWORD_CHANGED, accountId, accountId, Outcome.SUCCESS, now));
        return new Changed();
    }
}
