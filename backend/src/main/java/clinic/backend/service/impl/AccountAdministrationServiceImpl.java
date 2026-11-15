package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import clinic.backend.dto.request.CreateAccountRequest;
import clinic.backend.dto.request.AssignRoomRequest;
import clinic.backend.dto.request.LockAccountRequest;
import clinic.backend.dto.request.ResetAccountPasswordRequest;
import clinic.backend.dto.request.RoleChangeRequest;
import clinic.backend.dto.request.UnlockAccountRequest;
import clinic.backend.dto.request.UpdateAccountRequest;
import clinic.backend.dto.response.AccountActivityResponse;
import clinic.backend.dto.response.AccountDetailResponse;
import clinic.backend.dto.response.AccountHistoryResponse;
import clinic.backend.dto.response.AccountSummaryResponse;
import clinic.backend.dto.response.AllowedAccountActionsResponse;
import clinic.backend.dto.response.CreatedAccountResponse;
import clinic.backend.dto.response.DepartmentOptionResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RoleChangeCheckResponse;
import clinic.backend.dto.response.TemporaryPasswordResponse;
import clinic.backend.dto.response.WorkInProgressResponse;
import clinic.backend.entity.AuditLog;
import clinic.backend.entity.Department;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PasswordViolation;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.enums.TemporaryPasswordMode;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.AccountMapper;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.SecurityAuditEventRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffAccountRepository.AccountSummaryView;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.TemporaryPasswordGenerator;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AccountAdministrationService;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
public class AccountAdministrationServiceImpl implements AccountAdministrationService {

    private static final Pattern PRACTICE_LICENSE_FORMAT =
            Pattern.compile("^[\\p{L}\\p{N}/-]{5,50}$");

    /** Mirrors ck_staff_accounts_ma_lienthong_bacsi (V55). */
    private static final Pattern MA_LIENTHONG_BACSI_FORMAT = Pattern.compile("^[A-Za-z0-9]{1,50}$");
    private static final Pattern CITIZEN_ID_FORMAT = Pattern.compile("^[0-9]{12}$");
    /** The number itself never goes into the audit log (docs/security.md): only that it changed. */
    private static final String MASKED = "***";

    /** BR-59: a staff full name has 2–120 characters after trimming and collapsing repeated spaces. */
    private static final int FULL_NAME_MIN_LENGTH = 2;
    private static final int FULL_NAME_MAX_LENGTH = 120;

    /** BR-97: an action reason has 5–500 characters after trimming; the request DTO enforces the maximum. */
    private static final int LOCK_REASON_MIN_LENGTH = 5;

    private static final String ACCOUNT_ENTITY = "staff_accounts";

    private final StaffAccountRepository accountRepository;
    private final StaffRoomAssignmentRepository assignmentRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthSessionRepository sessionRepository;
    private final SecurityAuditEventRepository securityAuditRepository;
    private final AuditLogRepository auditLogRepository;
    private final VisitRepository visitRepository;
    private final ParaclinicalOrderDetailRepository orderDetailRepository;
    private final AuditLogService auditLogService;
    private final SecurityAuditService securityAuditService;
    private final AccountMapper accountMapper;
    private final PasswordHasher passwordHasher;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentOptionResponse> listDepartments() {
        return departmentRepository.findAllByOrderByDepartmentNameAsc().stream()
                .map(department -> new DepartmentOptionResponse(
                        department.getId(), department.getDepartmentCode(), department.getDepartmentName(),
                        department.getType(), department.isActive()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountSummaryResponse> listAccounts(
            String search, Role role, UUID departmentId, AccountStatus status,
            int page, int size, String sortField, boolean descending) {
        if (page < 0 || (size != 10 && size != 20 && size != 50)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    Map.of("page", "MUST_BE_NON_NEGATIVE", "size", "ALLOWED_VALUES_10_20_50"));
        }
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortProperty(sortField)).and(Sort.by(direction, "id"));
        String searchPattern = search == null || search.isBlank()
                ? ""
                : "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
        Page<AccountSummaryView> result = accountRepository.searchSummaries(
                searchPattern, role, departmentId, status, PageRequest.of(page, size, sort));
        List<AccountSummaryResponse> content = result.getContent().stream()
                .map(this::toSummary)
                .toList();
        return page(content, result);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDetailResponse getAccount(StaffPrincipal principal, UUID accountId) {
        return detail(requiredAccount(accountId), principal.accountId());
    }

    @Override
    @Transactional
    public CreatedAccountResponse createAccount(StaffPrincipal principal, CreateAccountRequest request) {
        StaffAccount prior = accountRepository.findByCreationSubmissionId(request.submissionId()).orElse(null);
        if (prior != null) {
            return new CreatedAccountResponse(prior.getId(), prior.getUsername(), null, true, needsRoom(prior.getRole()));
        }
        requireNonAdminRole(request.role());
        requireProfessionalInformation(request.role(), request.academicTitle(), request.practiceLicenseNo());
        requireValidStaffInformation(request.dateOfBirth(), request.practiceLicenseNo());
        String username = StaffAccount.normalizeUsername(request.username())
                .orElseThrow(() -> new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                        Map.of("username", "INVALID_FORMAT")));
        String email = normalizeEmail(request.email());
        String practiceLicenseNo = normalizeOptional(request.practiceLicenseNo());
        checkUniqueForCreate(username, request.phoneNumber(), email, practiceLicenseNo);
        String temporaryPassword = temporaryPassword(request.passwordMode(), request.initialPassword());
        Instant now = clock.instant();
        StaffAccount account = StaffAccount.create(
                ids.next(), username, passwordHasher.hash(temporaryPassword), normalizeFullName(request.fullName()),
                normalizeOptional(request.academicTitle()), practiceLicenseNo, request.dateOfBirth(), request.gender(),
                request.phoneNumber(), email, normalizeOptional(request.address()), request.role(),
                principal.accountId(), request.submissionId(),
                request.passwordMode() == TemporaryPasswordMode.GENERATE, now);
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw uniquenessConflict();
        }
        auditLogService.recordAccountChange(
                principal.accountId(), account.getId(), AuditLogAction.CREATE, Map.of(), Map.of(), now);
        securityAuditService.record(
                SecurityAuditEventType.ACCOUNT_CREATED, principal.accountId(), account.getId(),
                AuditOutcome.SUCCESS, now);
        return new CreatedAccountResponse(account.getId(), username, temporaryPassword, false, needsRoom(request.role()));
    }

    @Override
    @Transactional
    public AccountDetailResponse updateAccount(
            StaffPrincipal principal, UUID accountId, UpdateAccountRequest request) {
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        requireVersion(account, request.version());
        requireProfessionalInformation(account.getRole(), request.academicTitle(), request.practiceLicenseNo());
        requireValidStaffInformation(request.dateOfBirth(), request.practiceLicenseNo());
        String email = normalizeEmail(request.email());
        String practiceLicenseNo = normalizeOptional(request.practiceLicenseNo());
        String maLienthongBacsi = normalizeOptional(request.maLienthongBacsi());
        requireValidMaLienthongBacsi(account.getRole(), maLienthongBacsi);
        String citizenId = normalizeOptional(request.citizenId());
        requireValidCitizenId(account.getRole(), citizenId);
        checkUniqueForUpdate(account, request.phoneNumber(), email, practiceLicenseNo, maLienthongBacsi, citizenId);

        Map<String, Object> oldValues = editableValues(account);
        Map<String, Object> newValues = editableValues(request, email);
        removeUnchanged(oldValues, newValues);
        if (oldValues.isEmpty()) {
            return detail(account, principal.accountId());
        }
        Instant now = clock.instant();
        account.updatePersonalInformation(
                normalizeFullName(request.fullName()), normalizeOptional(request.academicTitle()), practiceLicenseNo, request.dateOfBirth(),
                request.gender(), request.phoneNumber(), email, normalizeOptional(request.address()), now);
        account.updateMaLienthongBacsi(maLienthongBacsi, now);
        account.updateCitizenId(citizenId, now);
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw uniquenessConflict();
        }
        recordChangedFields(principal.accountId(), account.getId(), oldValues, newValues, now);
        securityAuditService.record(
                SecurityAuditEventType.ACCOUNT_UPDATED, principal.accountId(), account.getId(),
                AuditOutcome.SUCCESS, now);
        return detail(account, principal.accountId());
    }

    @Override
    @Transactional
    public AccountDetailResponse assignRoom(
            StaffPrincipal principal, UUID accountId, AssignRoomRequest request) {
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        if (!needsRoom(account.getRole())) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("role", "ROOM_NOT_APPLICABLE"));
        }
        var room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        RoomType requiredType = account.getRole() == Role.DOCTOR ? RoomType.EXAMINATION : RoomType.PARACLINICAL;
        if (room.getStatus() != ActivationStatus.ACTIVE || room.getType() != requiredType) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("roomId", "NOT_ELIGIBLE"));
        }

        Instant now = clock.instant();
        StaffRoomAssignment current = assignmentRepository
                .findByStaffAccountIdAndRemovedAtIsNull(accountId).orElse(null);
        if (current != null && current.getRoomId().equals(room.getId())) {
            return detail(account, principal.accountId());
        }
        UUID oldRoomId = current == null ? null : current.getRoomId();
        if (current != null) {
            current.remove(principal.accountId(), now);
            assignmentRepository.save(current);
        }
        assignmentRepository.save(new StaffRoomAssignment(
                ids.next(), accountId, room.getId(), now, principal.accountId(), null, null, null));

        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        oldValues.put("roomId", oldRoomId);
        newValues.put("roomId", room.getId());
        auditLogService.recordAccountChange(
                principal.accountId(), accountId, AuditLogAction.UPDATE, oldValues, newValues, now);
        return detail(account, principal.accountId());
    }

    @Override
    @Transactional(readOnly = true)
    public RoleChangeCheckResponse checkRoleChange(UUID accountId, RoleChangeRequest request) {
        StaffAccount account = requiredAccount(accountId);
        validateRoleChange(account, request);
        validateProfessionalInformation(account, request);
        return roleCheck(account, request.newRole());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkInProgressResponse workInProgress(UUID accountId) {
        StaffAccount account = requiredAccount(accountId);
        return new WorkInProgressResponse(visitsInProgress(account), ordersInProgress(account));
    }

    @Override
    @Transactional
    public AccountDetailResponse changeRole(
            StaffPrincipal principal, UUID accountId, RoleChangeRequest request) {
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        validateRoleChange(account, request);
        validateProfessionalInformation(account, request);
        RoleChangeCheckResponse check = roleCheck(account, request.newRole());
        if (check.visitsInProgress() + check.ordersInProgress() > 0) {
            throw new AppException(ErrorCode.WORK_IN_PROGRESS, Map.of(
                    "visits", Long.toString(check.visitsInProgress()),
                    "orders", Long.toString(check.ordersInProgress())));
        }
        Instant now = clock.instant();
        Role oldRole = account.getRole();
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        oldValues.put("role", oldRole);
        newValues.put("role", request.newRole());
        if (needsRoom(request.newRole())) {
            // I1-D47: a Doctor or Technician always has an academic title and practice-license number.
            String title = request.academicTitle().strip();
            String license = request.practiceLicenseNo().strip();
            putIfChanged(oldValues, newValues, "academicTitle", account.getAcademicTitle(), title);
            putIfChanged(oldValues, newValues, "practiceLicenseNo", account.getPracticeLicenseNo(), license);
            account.updateProfessionalInformation(title, license, now);
        }
        account.changeRole(request.newRole(), now);
        if (needsRoom(oldRole)) {
            assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(accountId)
                    .ifPresent(assignment -> assignment.remove(principal.accountId(), now));
        }
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw uniquenessConflict();
        }
        sessionRepository.revokeAllForAccount(accountId, SessionRevocationReason.ROLE_CHANGED, now);
        auditLogService.recordAccountChange(
                principal.accountId(), accountId, AuditLogAction.UPDATE, oldValues, newValues, now);
        securityAuditService.record(
                SecurityAuditEventType.ROLE_CHANGED, principal.accountId(), accountId, AuditOutcome.SUCCESS, now);
        return detail(account, principal.accountId());
    }

    @Override
    @Transactional
    public AccountDetailResponse lock(StaffPrincipal principal, UUID accountId, LockAccountRequest request) {
        String reason = request.reason().strip();
        if (reason.length() < LOCK_REASON_MIN_LENGTH) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("reason", "TOO_SHORT"));
        }
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        requireState(account, request.expectedStatus(), request.expectedVersion());
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_STATE_CONFLICT);
        }
        long visits = visitsInProgress(account);
        long orders = ordersInProgress(account);
        if (visits + orders > 0) {
            throw new AppException(ErrorCode.WORK_IN_PROGRESS, Map.of(
                    "visits", Long.toString(visits),
                    "orders", Long.toString(orders)));
        }
        Instant now = clock.instant();
        account.lock(reason, principal.accountId(), now);
        accountRepository.saveAndFlush(account);
        sessionRepository.revokeAllForAccount(accountId, SessionRevocationReason.ACCOUNT_LOCKED, now);
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        oldValues.put("status", AccountStatus.ACTIVE);
        oldValues.put("lockReason", null);
        newValues.put("status", AccountStatus.LOCKED);
        newValues.put("lockReason", reason);
        auditLogService.recordAccountChange(
                principal.accountId(), accountId, AuditLogAction.LOCK, oldValues, newValues, now);
        securityAuditService.record(
                SecurityAuditEventType.ACCOUNT_LOCKED, principal.accountId(), accountId, AuditOutcome.SUCCESS, now);
        return detail(account, principal.accountId());
    }

    @Override
    @Transactional
    public AccountDetailResponse unlock(StaffPrincipal principal, UUID accountId, UnlockAccountRequest request) {
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        requireState(account, request.expectedStatus(), request.expectedVersion());
        if (account.getStatus() != AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        String oldReason = account.getLockReason();
        account.unlock(now);
        accountRepository.saveAndFlush(account);
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        oldValues.put("status", AccountStatus.LOCKED);
        oldValues.put("lockReason", oldReason);
        newValues.put("status", AccountStatus.ACTIVE);
        newValues.put("lockReason", null);
        auditLogService.recordAccountChange(
                principal.accountId(), accountId, AuditLogAction.UNLOCK, oldValues, newValues, now);
        securityAuditService.record(
                SecurityAuditEventType.ACCOUNT_UNLOCKED, principal.accountId(), accountId, AuditOutcome.SUCCESS, now);
        return detail(account, principal.accountId());
    }

    @Override
    @Transactional
    public TemporaryPasswordResponse resetPassword(
            StaffPrincipal principal, UUID accountId, ResetAccountPasswordRequest request) {
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        requireVersion(account, request.expectedVersion());
        String temporaryPassword = temporaryPassword(request.mode(), request.password());
        Instant now = clock.instant();
        String passwordHash = passwordHasher.hash(temporaryPassword);
        if (request.mode() == TemporaryPasswordMode.GENERATE) {
            account.resetPasswordForRecovery(passwordHash, now);
        } else {
            account.resetPasswordWithoutRequiredChange(passwordHash, now);
        }
        accountRepository.saveAndFlush(account);
        sessionRepository.revokeAllForAccount(accountId, SessionRevocationReason.PASSWORD_RESET, now);
        securityAuditService.record(
                SecurityAuditEventType.PASSWORD_RESET, principal.accountId(), accountId, AuditOutcome.SUCCESS, now);
        return new TemporaryPasswordResponse(temporaryPassword);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountActivityResponse> recentActivities(UUID accountId, int limit) {
        requiredAccount(accountId);
        List<SecurityAuditEventType> types = List.of(
                SecurityAuditEventType.LOGIN_SUCCEEDED, SecurityAuditEventType.LOGIN_FAILED,
                SecurityAuditEventType.LOGIN_THROTTLED, SecurityAuditEventType.LOGOUT);
        return securityAuditRepository.findByTargetAccountIdAndEventTypeInOrderByOccurredAtDesc(
                        accountId, types, PageRequest.of(0, limit))
                .stream()
                .map(event -> new AccountActivityResponse(
                        event.getId(), event.getOccurredAt(), event.getEventType(), event.getOutcome(),
                        event.getIpAddress()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountHistoryResponse> changeHistory(UUID accountId, int page, int size) {
        requiredAccount(accountId);
        Page<AuditLog> result = auditLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
                ACCOUNT_ENTITY, accountId, PageRequest.of(page, size));
        List<AccountHistoryResponse> content = result.getContent().stream()
                .map(item -> new AccountHistoryResponse(
                        item.getId(), item.getOccurredAt(), item.getActorAccountId(), item.getAction(),
                        item.getChangedFields(), item.getOldValue(), item.getNewValue()))
                .toList();
        return page(content, result);
    }

    private String sortProperty(String requested) {
        return switch (requested == null ? "createdAt" : requested) {
            case "username" -> "username";
            case "fullName" -> "fullName";
            case "role" -> "role";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            case "lastLoginAt" -> "lastLoginAt";
            default -> throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of("sort", "NOT_ALLOWED"));
        };
    }

    private StaffAccount requiredAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private AccountDetailResponse detail(StaffAccount account, UUID requesterId) {
        Location location = location(account.getId());
        boolean protectedAccount = account.getRole() == Role.ADMIN || account.getId().equals(requesterId);
        AllowedAccountActionsResponse actions = new AllowedAccountActionsResponse(
                !protectedAccount, !protectedAccount, !protectedAccount && account.getStatus() == AccountStatus.ACTIVE,
                !protectedAccount && account.getStatus() == AccountStatus.LOCKED, !protectedAccount,
                !protectedAccount && needsRoom(account.getRole()));
        return accountMapper.toDetail(account, location.roomName(), location.departmentName(), actions);
    }

    private AccountSummaryResponse toSummary(AccountSummaryView account) {
        return new AccountSummaryResponse(
                account.getId(), account.getUsername(), account.getFullName(), account.getPhoneNumber(),
                account.getEmail(), account.getRole(), account.getStatus(),
                account.getRoomId(), account.getRoomCode(), account.getRoomName(),
                account.getDepartmentName(), account.getLastLoginAt(), account.getPasswordChangeRequired(),
                account.getVersion());
    }

    private Location location(UUID accountId) {
        return assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(accountId)
                .flatMap(assignment -> roomRepository.findById(assignment.getRoomId()))
                .map(room -> new Location(room.getRoomName(), departmentRepository.findById(room.getDepartmentId())
                        .map(Department::getDepartmentName).orElse(null)))
                .orElse(Location.NONE);
    }

    private void rejectAdminOrSelf(StaffPrincipal principal, StaffAccount account) {
        if (account.getRole() == Role.ADMIN) {
            throw new AppException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        if (account.getId().equals(principal.accountId())) {
            throw new AppException(ErrorCode.SELF_ADMINISTRATION_FORBIDDEN);
        }
    }

    private void validateRoleChange(StaffAccount account, RoleChangeRequest request) {
        if (account.getRole() == Role.ADMIN || request.newRole() == Role.ADMIN) {
            throw new AppException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        if (account.getRole() != request.expectedRole()) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (account.getRole() == request.newRole()) {
            throw new AppException(ErrorCode.ROLE_CHANGE_NOT_ALLOWED, Map.of("newRole", "UNCHANGED"));
        }
    }

    /** I1-D47: a change to Doctor or Technician carries a valid academic title and unique practice license. */
    private void validateProfessionalInformation(StaffAccount account, RoleChangeRequest request) {
        if (!needsRoom(request.newRole())) {
            return;
        }
        requireProfessionalInformation(request.newRole(), request.academicTitle(), request.practiceLicenseNo());
        requireValidStaffInformation(null, request.practiceLicenseNo());
        String license = request.practiceLicenseNo().strip();
        if (accountRepository.existsByPracticeLicenseNoAndIdNot(license, account.getId())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, Map.of("practiceLicenseNo", "ALREADY_EXISTS"));
        }
    }

    private static void putIfChanged(
            Map<String, Object> oldValues, Map<String, Object> newValues, String field, String oldValue, String newValue) {
        if (!newValue.equals(oldValue)) {
            oldValues.put(field, oldValue);
            newValues.put(field, newValue);
        }
    }

    private RoleChangeCheckResponse roleCheck(StaffAccount account, Role newRole) {
        long visits = visitsInProgress(account);
        long orders = ordersInProgress(account);
        return new RoleChangeCheckResponse(
                account.getRole(), newRole, visits, orders, needsRoom(account.getRole()), needsRoom(newRole));
    }

    private long visitsInProgress(StaffAccount account) {
        return visitRepository.countByDoctorAccountIdAndStatus(account.getId(), VisitStatus.IN_EXAMINATION);
    }

    private long ordersInProgress(StaffAccount account) {
        return orderDetailRepository.countByPerformerAccountIdAndStatus(
                account.getId(), ParaclinicalOrderDetailStatus.IN_PROGRESS);
    }

    private void checkUniqueForCreate(
            String username, String phone, String email, String practiceLicenseNo) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        if (accountRepository.existsByUsername(username)) conflicts.put("username", "ALREADY_EXISTS");
        if (accountRepository.existsByPhoneNumber(phone)) conflicts.put("phoneNumber", "ALREADY_EXISTS");
        if (email != null && accountRepository.existsByEmailIgnoreCase(email)) conflicts.put("email", "ALREADY_EXISTS");
        if (practiceLicenseNo != null && accountRepository.existsByPracticeLicenseNo(practiceLicenseNo)) {
            conflicts.put("practiceLicenseNo", "ALREADY_EXISTS");
        }
        if (!conflicts.isEmpty()) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, conflicts);
    }

    private void recordChangedFields(
            UUID actorAccountId,
            UUID targetAccountId,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            Instant occurredAt) {
        oldValues.forEach((field, oldValue) -> {
            Map<String, Object> oldField = new LinkedHashMap<>();
            Map<String, Object> newField = new LinkedHashMap<>();
            Object newValue = newValues.get(field);
            boolean sensitive = "citizenId".equals(field);
            oldField.put(field, sensitive && oldValue != null ? MASKED : oldValue);
            newField.put(field, sensitive && newValue != null ? MASKED : newValue);
            auditLogService.recordAccountChange(
                    actorAccountId, targetAccountId, AuditLogAction.UPDATE, oldField, newField, occurredAt);
        });
    }

    private void checkUniqueForUpdate(
            StaffAccount account, String phone, String email, String practiceLicenseNo, String maLienthongBacsi,
            String citizenId) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        if (accountRepository.existsByPhoneNumberAndIdNot(phone, account.getId())) {
            conflicts.put("phoneNumber", "ALREADY_EXISTS");
        }
        if (email != null && accountRepository.existsByEmailIgnoreCaseAndIdNot(email, account.getId())) {
            conflicts.put("email", "ALREADY_EXISTS");
        }
        if (practiceLicenseNo != null
                && accountRepository.existsByPracticeLicenseNoAndIdNot(practiceLicenseNo, account.getId())) {
            conflicts.put("practiceLicenseNo", "ALREADY_EXISTS");
        }
        if (maLienthongBacsi != null
                && accountRepository.existsByMaLienthongBacsiAndIdNot(maLienthongBacsi, account.getId())) {
            conflicts.put("maLienthongBacsi", "ALREADY_EXISTS");
        }
        if (citizenId != null && accountRepository.existsByCitizenIdAndIdNot(citizenId, account.getId())) {
            conflicts.put("citizenId", "ALREADY_EXISTS");
        }
        if (!conflicts.isEmpty()) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, conflicts);
    }

    /** BR-49, BR-65: only a Doctor prescribes, so only a Doctor account holds a prescriber link code. */
    private void requireValidMaLienthongBacsi(Role role, String maLienthongBacsi) {
        if (maLienthongBacsi == null) return;
        if (role != Role.DOCTOR) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("maLienthongBacsi", "NOT_APPLICABLE"));
        }
        if (!MA_LIENTHONG_BACSI_FORMAT.matcher(maLienthongBacsi).matches()) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("maLienthongBacsi", "INVALID_FORMAT"));
        }
    }

    /** BR-59-16: only a Doctor signs prescriptions, so only a Doctor account holds the number; 12 digits. */
    private void requireValidCitizenId(Role role, String citizenId) {
        if (citizenId == null) return;
        if (role != Role.DOCTOR) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("citizenId", "NOT_APPLICABLE"));
        }
        if (!CITIZEN_ID_FORMAT.matcher(citizenId).matches()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("citizenId", "INVALID_FORMAT"));
        }
    }

    private AppException uniquenessConflict() {
        return new AppException(ErrorCode.CONCURRENT_MODIFICATION, Map.of("fields", "UNIQUENESS_CONFLICT"));
    }

    private String temporaryPassword(TemporaryPasswordMode mode, String manualPassword) {
        String value = mode == TemporaryPasswordMode.GENERATE ? passwordGenerator.generate() : manualPassword;
        PasswordViolation violation = StaffAccount.checkPasswordPolicy(value).orElse(null);
        if (violation != null) {
            throw new AppException(ErrorCode.PASSWORD_POLICY_VIOLATION, Map.of("password", violation.name()));
        }
        return value;
    }

    /** BR-59: trims the full name, collapses repeated spaces and checks the 2–120 character length. */
    static String normalizeFullName(String fullName) {
        String normalized = fullName == null ? "" : fullName.strip().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < FULL_NAME_MIN_LENGTH) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("fullName", "INVALID_FORMAT"));
        }
        if (length > FULL_NAME_MAX_LENGTH) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("fullName", "TOO_LONG"));
        }
        return normalized;
    }

    private void requireProfessionalInformation(Role role, String title, String practiceLicenseNo) {
        if (needsRoom(role) && (title == null || title.isBlank())) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("academicTitle", "REQUIRED"));
        }
        if (needsRoom(role) && (practiceLicenseNo == null || practiceLicenseNo.isBlank())) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("practiceLicenseNo", "REQUIRED"));
        }
    }

    private void requireValidStaffInformation(LocalDate dateOfBirth, String practiceLicenseNo) {
        if (dateOfBirth != null) {
            LocalDate today = LocalDate.now(clock);
            if (dateOfBirth.isAfter(today.minusYears(18))) {
                throw new AppException(
                        ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("dateOfBirth", "INVALID_FORMAT"));
            }
        }
        String normalizedLicense = normalizeOptional(practiceLicenseNo);
        if (normalizedLicense != null && !PRACTICE_LICENSE_FORMAT.matcher(normalizedLicense).matches()) {
            throw new AppException(
                    ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("practiceLicenseNo", "INVALID_FORMAT"));
        }
    }

    private void requireNonAdminRole(Role role) {
        if (role == Role.ADMIN) throw new AppException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
    }

    private void requireState(StaffAccount account, AccountStatus expectedStatus, Long expectedVersion) {
        requireVersion(account, expectedVersion);
        if (account.getStatus() != expectedStatus) throw new AppException(ErrorCode.ACCOUNT_STATE_CONFLICT);
    }

    private void requireVersion(StaffAccount account, Long expectedVersion) {
        if (!Objects.equals(account.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private Map<String, Object> editableValues(StaffAccount account) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", account.getFullName());
        values.put("academicTitle", account.getAcademicTitle());
        values.put("practiceLicenseNo", account.getPracticeLicenseNo());
        values.put("maLienthongBacsi", account.getMaLienthongBacsi());
        values.put("citizenId", account.getCitizenId());
        values.put("dateOfBirth", account.getDateOfBirth());
        values.put("gender", account.getGender());
        values.put("phoneNumber", account.getPhoneNumber());
        values.put("email", account.getEmail());
        values.put("address", account.getAddress());
        return values;
    }

    private Map<String, Object> editableValues(UpdateAccountRequest request, String email) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", normalizeFullName(request.fullName()));
        values.put("academicTitle", normalizeOptional(request.academicTitle()));
        values.put("practiceLicenseNo", normalizeOptional(request.practiceLicenseNo()));
        values.put("maLienthongBacsi", normalizeOptional(request.maLienthongBacsi()));
        values.put("citizenId", normalizeOptional(request.citizenId()));
        values.put("dateOfBirth", request.dateOfBirth());
        values.put("gender", request.gender());
        values.put("phoneNumber", request.phoneNumber());
        values.put("email", email);
        values.put("address", normalizeOptional(request.address()));
        return values;
    }

    private void removeUnchanged(Map<String, Object> oldValues, Map<String, Object> newValues) {
        List<String> unchanged = oldValues.keySet().stream()
                .filter(key -> Objects.equals(oldValues.get(key), newValues.get(key))).toList();
        unchanged.forEach(key -> {
            oldValues.remove(key);
            newValues.remove(key);
        });
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private boolean needsRoom(Role role) {
        return role == Role.DOCTOR || role == Role.TECHNICIAN;
    }

    private <T> PageResponse<T> page(List<T> content, Page<?> source) {
        return new PageResponse<>(content, new PageResponse.PageMetadata(
                source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages()));
    }

    private record Location(String roomName, String departmentName) {
        private static final Location NONE = new Location(null, null);
    }
}
