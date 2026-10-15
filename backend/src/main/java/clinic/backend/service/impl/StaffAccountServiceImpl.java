package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PasswordViolation;
import clinic.backend.enums.Role;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.service.StaffAccountService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffAccountServiceImpl implements StaffAccountService {

    private static final String NEW_PASSWORD_FIELD = "newPassword";

    private final StaffAccountRepository staffAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final SecurityAuditService securityAuditService;
    private final PasswordHasher passwordHasher;
    private final UuidV7Generator ids;
    private final Clock clock;

    /**
     * Rejections are thrown as {@link AppException} but do not roll back, so the failed-attempt counter is
     * committed.
     */
    @Override
    @Transactional(noRollbackFor = AppException.class)
    public void changePassword(StaffPrincipal principal, ChangePasswordRequest request) {
        Instant now = clock.instant();
        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        // The account no longer exists or is locked: the caller must authenticate again.
        StaffAccount account = staffAccountRepository.findById(principal.accountId())
                .filter(StaffAccount::canAuthenticate)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        // ASSUMPTION (UC-3): wrong current passwords count toward the login throttle.
        if (account.isLoginBlocked(now)) {
            passwordHasher.simulateMatch(currentPassword);
            throw new AppException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        if (!passwordHasher.matches(currentPassword, account.getPasswordHash())) {
            if (account.registerFailedLogin(now)) {
                securityAuditService.record(
                        SecurityAuditEventType.LOGIN_THROTTLED, account.getId(), account.getId(),
                        AuditOutcome.FAILURE, now);
            }
            staffAccountRepository.saveAndFlush(account);
            throw new AppException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }

        Optional<PasswordViolation> violation = StaffAccount.checkPasswordPolicy(newPassword);
        if (violation.isEmpty() && newPassword.equals(currentPassword)) {
            violation = Optional.of(PasswordViolation.SAME_AS_CURRENT);
        }
        if (violation.isPresent()) {
            throw new AppException(
                    ErrorCode.PASSWORD_POLICY_VIOLATION, Map.of(NEW_PASSWORD_FIELD, violation.get().name()));
        }

        account.clearFailedLoginAttempts(now);
        account.changePassword(passwordHasher.hash(newPassword), now);
        staffAccountRepository.saveAndFlush(account);
        authSessionRepository.revokeAllForAccountExcept(
                account.getId(), principal.sessionId(), SessionRevocationReason.PASSWORD_CHANGED, now);
        securityAuditService.record(
                SecurityAuditEventType.PASSWORD_CHANGED, account.getId(), account.getId(), AuditOutcome.SUCCESS, now);
    }

    @Override
    @Transactional
    public Optional<StaffAccount> createFirstAdminIfMissing(String username, String password, String fullName) {
        if (staffAccountRepository.existsByRole(Role.ADMIN)) {
            return Optional.empty();
        }

        String adminUsername = StaffAccount.normalizeUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "The bootstrap Admin username does not match the username format"));
        if (StaffAccount.checkPasswordPolicy(password).isPresent()) {
            throw new IllegalStateException("The bootstrap Admin password does not satisfy the password policy");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalStateException("The bootstrap Admin full name must not be blank");
        }

        Instant now = clock.instant();
        StaffAccount admin = staffAccountRepository.saveAndFlush(StaffAccount.create(
                ids.next(),
                adminUsername,
                passwordHasher.hash(password),
                fullName.strip(),
                null,
                null,
                null,
                Role.ADMIN,
                now));

        securityAuditService.record(
                SecurityAuditEventType.ACCOUNT_CREATED, null, admin.getId(), AuditOutcome.SUCCESS, now);
        return Optional.of(admin);
    }

    @Override
    @Transactional
    public int assignDemoAccountPassword(String rawPassword) {
        if (StaffAccount.checkPasswordPolicy(rawPassword).isPresent()) {
            throw new IllegalStateException("The demo account password does not satisfy the password policy");
        }
        List<StaffAccount> pending = staffAccountRepository.findAllByPasswordHash(UNSET_DEMO_PASSWORD_HASH);
        if (pending.isEmpty()) {
            return 0;
        }
        // One hash for all demo accounts keeps startup fast; this runs only with the dev profile.
        String passwordHash = passwordHasher.hash(rawPassword);
        Instant now = clock.instant();
        for (StaffAccount account : pending) {
            account.changePassword(passwordHash, now);
            staffAccountRepository.saveAndFlush(account);
        }
        return pending.size();
    }
}
