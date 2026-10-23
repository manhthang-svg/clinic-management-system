package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.dto.request.UpdateProfileRequest;
import clinic.backend.dto.response.ProfileResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PasswordViolation;
import clinic.backend.enums.Role;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.ProfileMapper;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.TemporaryPasswordGenerator;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AdminRecoveryResult;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.service.StaffAccountService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffAccountServiceImpl implements StaffAccountService {

    private static final String NEW_PASSWORD_FIELD = "newPassword";

    private final StaffAccountRepository staffAccountRepository;
    private final StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuditLogService auditLogService;
    private final SecurityAuditService securityAuditService;
    private final ProfileMapper profileMapper;
    private final PasswordHasher passwordHasher;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
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
    @Transactional(readOnly = true)
    public ProfileResponse getOwnProfile(StaffPrincipal principal) {
        StaffAccount account = activeAccount(principal);
        return toProfile(account);
    }

    @Override
    @Transactional
    @PreAuthorize("!hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    public ProfileResponse updateOwnProfile(StaffPrincipal principal, UpdateProfileRequest request) {
        StaffAccount account = activeAccount(principal);
        String normalizedEmail = request.email() == null || request.email().isBlank()
                ? null : request.email().strip().toLowerCase(Locale.ROOT);

        boolean phoneChanged = !request.phoneNumber().equals(account.getPhoneNumber());
        boolean emailChanged = !Objects.equals(normalizedEmail, account.getEmail());
        if (!phoneChanged && !emailChanged) {
            return toProfile(account);
        }

        Map<String, String> conflicts = new LinkedHashMap<>();
        if (phoneChanged && staffAccountRepository.existsByPhoneNumberAndIdNot(request.phoneNumber(), account.getId())) {
            conflicts.put("phoneNumber", "ALREADY_EXISTS");
        }
        if (emailChanged && normalizedEmail != null
                && staffAccountRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, account.getId())) {
            conflicts.put("email", "ALREADY_EXISTS");
        }
        if (!conflicts.isEmpty()) {
            ErrorCode code = conflicts.size() == 1 && conflicts.containsKey("email")
                    ? ErrorCode.EMAIL_ALREADY_EXISTS
                    : conflicts.size() == 1 ? ErrorCode.PHONE_ALREADY_EXISTS : ErrorCode.CONCURRENT_MODIFICATION;
            throw new AppException(code, conflicts);
        }

        Map<String, String> oldValues = new LinkedHashMap<>();
        Map<String, String> newValues = new LinkedHashMap<>();
        if (phoneChanged) {
            oldValues.put("phoneNumber", account.getPhoneNumber());
            newValues.put("phoneNumber", request.phoneNumber());
        }
        if (emailChanged) {
            oldValues.put("email", account.getEmail());
            newValues.put("email", normalizedEmail);
        }

        Instant now = clock.instant();
        account.updateContact(request.phoneNumber(), normalizedEmail, now);
        try {
            staffAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            // The database unique index closes the race between the pre-check and this write.
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, Map.of("fields", "UNIQUENESS_CONFLICT"));
        }
        auditLogService.recordAccountUpdate(account.getId(), oldValues, newValues, now);
        securityAuditService.record(
                SecurityAuditEventType.ACCOUNT_UPDATED, account.getId(), account.getId(), AuditOutcome.SUCCESS, now);
        return toProfile(account);
    }

    private StaffAccount activeAccount(StaffPrincipal principal) {
        return staffAccountRepository.findById(principal.accountId())
                .filter(StaffAccount::canAuthenticate)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    private ProfileResponse toProfile(StaffAccount account) {
        ProfileLocation location = staffRoomAssignmentRepository
                .findByStaffAccountIdAndRemovedAtIsNull(account.getId())
                .flatMap(assignment -> roomRepository.findById(assignment.getRoomId()))
                .map(this::toLocation)
                .orElse(ProfileLocation.NONE);
        return profileMapper.toProfileResponse(account, location.roomName(), location.departmentName());
    }

    private ProfileLocation toLocation(Room room) {
        String departmentName = departmentRepository.findById(room.getDepartmentId())
                .map(Department::getDepartmentName)
                .orElse(null);
        return new ProfileLocation(room.getRoomName(), departmentName);
    }

    private record ProfileLocation(String roomName, String departmentName) {
        private static final ProfileLocation NONE = new ProfileLocation(null, null);
    }

    @Override
    @Transactional
    public AdminRecoveryResult recoverAdmin() {
        StaffAccount admin = staffAccountRepository.findByRole(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("No Admin account exists to recover"));
        Instant now = clock.instant();
        String temporaryPassword = temporaryPasswordGenerator.generate();

        admin.resetPasswordForRecovery(passwordHasher.hash(temporaryPassword), now);
        staffAccountRepository.saveAndFlush(admin);
        authSessionRepository.revokeAllForAccount(admin.getId(), SessionRevocationReason.PASSWORD_RESET, now);
        securityAuditService.record(
                SecurityAuditEventType.PASSWORD_RESET, null, admin.getId(), AuditOutcome.SUCCESS, now);

        return new AdminRecoveryResult(admin.getId(), admin.getUsername(), temporaryPassword);
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
