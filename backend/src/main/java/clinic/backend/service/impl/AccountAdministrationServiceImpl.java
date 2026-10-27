package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.dto.request.CreateAccountRequest;
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
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PasswordViolation;
import clinic.backend.enums.Role;
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
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.TemporaryPasswordGenerator;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AccountAdministrationService;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.utils.UuidV7Generator;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
public class AccountAdministrationServiceImpl implements AccountAdministrationService {

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
        Page<StaffAccount> result = accountRepository.findAll(
                accountSpecification(search, role, departmentId, status), PageRequest.of(page, size, sort));
        List<AccountSummaryResponse> content = result.getContent().stream()
                .map(account -> {
                    Location location = location(account.getId());
                    return accountMapper.toSummary(account, location.roomName(), location.departmentName());
                })
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
        requireAcademicTitle(request.role(), request.academicTitle());
        String username = StaffAccount.normalizeUsername(request.username())
                .orElseThrow(() -> new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                        Map.of("username", "INVALID_FORMAT")));
        String email = normalizeEmail(request.email());
        checkUniqueForCreate(username, request.phoneNumber(), email);
        String temporaryPassword = temporaryPassword(request.passwordMode(), request.initialPassword());
        Instant now = clock.instant();
        StaffAccount account = StaffAccount.create(
                ids.next(), username, passwordHasher.hash(temporaryPassword), request.fullName().strip(),
                normalizeOptional(request.academicTitle()), request.dateOfBirth(), request.gender(),
                request.phoneNumber(), email, normalizeOptional(request.address()), request.role(),
                principal.accountId(), request.submissionId(), now);
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
        requireAcademicTitle(account.getRole(), request.academicTitle());
        String email = normalizeEmail(request.email());
        checkUniqueForUpdate(account, request.phoneNumber(), email);

        Map<String, Object> oldValues = editableValues(account);
        Map<String, Object> newValues = editableValues(request, email);
        removeUnchanged(oldValues, newValues);
        if (oldValues.isEmpty()) {
            return detail(account, principal.accountId());
        }
        Instant now = clock.instant();
        account.updatePersonalInformation(
                request.fullName().strip(), normalizeOptional(request.academicTitle()), request.dateOfBirth(),
                request.gender(), request.phoneNumber(), email, normalizeOptional(request.address()), now);
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
    @Transactional(readOnly = true)
    public RoleChangeCheckResponse checkRoleChange(UUID accountId, RoleChangeRequest request) {
        StaffAccount account = requiredAccount(accountId);
        validateRoleChange(account, request);
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
        RoleChangeCheckResponse check = roleCheck(account, request.newRole());
        if (check.visitsInProgress() + check.ordersInProgress() > 0) {
            throw new AppException(ErrorCode.WORK_IN_PROGRESS, Map.of(
                    "visits", Long.toString(check.visitsInProgress()),
                    "orders", Long.toString(check.ordersInProgress())));
        }
        Instant now = clock.instant();
        Role oldRole = account.getRole();
        account.changeRole(request.newRole(), now);
        if (needsRoom(oldRole)) {
            assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(accountId)
                    .ifPresent(assignment -> assignment.remove(principal.accountId(), now));
        }
        accountRepository.saveAndFlush(account);
        sessionRepository.revokeAllForAccount(accountId, SessionRevocationReason.ROLE_CHANGED, now);
        auditLogService.recordAccountChange(
                principal.accountId(), accountId, AuditLogAction.UPDATE,
                Map.of("role", oldRole), Map.of("role", request.newRole()), now);
        securityAuditService.record(
                SecurityAuditEventType.ROLE_CHANGED, principal.accountId(), accountId, AuditOutcome.SUCCESS, now);
        return detail(account, principal.accountId());
    }

    @Override
    @Transactional
    public AccountDetailResponse lock(StaffPrincipal principal, UUID accountId, LockAccountRequest request) {
        StaffAccount account = requiredAccount(accountId);
        rejectAdminOrSelf(principal, account);
        requireState(account, request.expectedStatus(), request.expectedVersion());
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        account.lock(request.reason().strip(), principal.accountId(), now);
        accountRepository.saveAndFlush(account);
        sessionRepository.revokeAllForAccount(accountId, SessionRevocationReason.ACCOUNT_LOCKED, now);
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        oldValues.put("status", AccountStatus.ACTIVE);
        oldValues.put("lockReason", null);
        newValues.put("status", AccountStatus.LOCKED);
        newValues.put("lockReason", request.reason().strip());
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
        account.resetPassword(passwordHasher.hash(temporaryPassword), now);
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

    private Specification<StaffAccount> accountSpecification(
            String search, Role role, UUID departmentId, AccountStatus status) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (search != null && !search.isBlank()) {
                String term = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
                var searchable = cb.concat(cb.concat(cb.concat(root.get("username"), " "), root.get("fullName")),
                        cb.concat(cb.concat(" ", cb.coalesce(root.get("phoneNumber"), "")),
                                cb.concat(" ", cb.coalesce(root.get("email"), ""))));
                predicate = cb.and(predicate, cb.like(cb.lower(cb.function("unaccent", String.class, searchable)),
                        cb.function("unaccent", String.class, cb.literal(term))));
            }
            if (role != null) {
                predicate = cb.and(predicate, cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (departmentId != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<StaffRoomAssignment> assignment = subquery.from(StaffRoomAssignment.class);
                Root<Room> room = subquery.from(Room.class);
                subquery.select(assignment.get("id")).where(
                        cb.equal(assignment.get("staffAccountId"), root.get("id")),
                        cb.isNull(assignment.get("removedAt")),
                        cb.equal(room.get("id"), assignment.get("roomId")),
                        cb.equal(room.get("departmentId"), departmentId));
                predicate = cb.and(predicate, cb.exists(subquery));
            }
            return predicate;
        };
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

    private void checkUniqueForCreate(String username, String phone, String email) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        if (accountRepository.existsByUsername(username)) conflicts.put("username", "ALREADY_EXISTS");
        if (accountRepository.existsByPhoneNumber(phone)) conflicts.put("phoneNumber", "ALREADY_EXISTS");
        if (email != null && accountRepository.existsByEmailIgnoreCase(email)) conflicts.put("email", "ALREADY_EXISTS");
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
            oldField.put(field, oldValue);
            newField.put(field, newValues.get(field));
            auditLogService.recordAccountChange(
                    actorAccountId, targetAccountId, AuditLogAction.UPDATE, oldField, newField, occurredAt);
        });
    }

    private void checkUniqueForUpdate(StaffAccount account, String phone, String email) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        if (accountRepository.existsByPhoneNumberAndIdNot(phone, account.getId())) {
            conflicts.put("phoneNumber", "ALREADY_EXISTS");
        }
        if (email != null && accountRepository.existsByEmailIgnoreCaseAndIdNot(email, account.getId())) {
            conflicts.put("email", "ALREADY_EXISTS");
        }
        if (!conflicts.isEmpty()) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, conflicts);
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

    private void requireAcademicTitle(Role role, String title) {
        if (needsRoom(role) && (title == null || title.isBlank())) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("academicTitle", "REQUIRED"));
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
        values.put("dateOfBirth", account.getDateOfBirth());
        values.put("gender", account.getGender());
        values.put("phoneNumber", account.getPhoneNumber());
        values.put("email", account.getEmail());
        values.put("address", account.getAddress());
        return values;
    }

    private Map<String, Object> editableValues(UpdateAccountRequest request, String email) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", request.fullName().strip());
        values.put("academicTitle", normalizeOptional(request.academicTitle()));
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