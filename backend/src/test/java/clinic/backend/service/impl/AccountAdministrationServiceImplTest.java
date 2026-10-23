package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.RoleChangeRequest;
import clinic.backend.dto.request.UpdateAccountRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
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
import clinic.backend.service.AuditLogService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;

@ExtendWith(MockitoExtension.class)
class AccountAdministrationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    @Mock StaffAccountRepository accountRepository;
    @Mock StaffRoomAssignmentRepository assignmentRepository;
    @Mock RoomRepository roomRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuthSessionRepository sessionRepository;
    @Mock SecurityAuditEventRepository securityAuditRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock VisitRepository visitRepository;
    @Mock ParaclinicalOrderDetailRepository orderDetailRepository;
    @Mock AuditLogService auditLogService;
    @Mock SecurityAuditService securityAuditService;
    @Mock AccountMapper accountMapper;
    @Mock PasswordHasher passwordHasher;
    @Mock TemporaryPasswordGenerator passwordGenerator;
    @Mock UuidV7Generator ids;
    @Mock Clock clock;

    private AccountAdministrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountAdministrationServiceImpl(
                accountRepository, assignmentRepository, roomRepository, departmentRepository, sessionRepository,
                securityAuditRepository, auditLogRepository, visitRepository, orderDetailRepository,
                auditLogService, securityAuditService, accountMapper, passwordHasher, passwordGenerator, ids, clock);
    }

    @Test
    void roleChangeIsRejectedWhileTheTargetHasAVisitInExamination() {
        StaffAccount target = account(Role.RECEPTIONIST);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(visitRepository.countByDoctorAccountIdAndStatus(target.getId(), VisitStatus.IN_EXAMINATION))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(),
                new RoleChangeRequest(Role.DOCTOR, Role.RECEPTIONIST)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WORK_IN_PROGRESS));
    }

    @Test
    void seededAdminCannotBeChangedByRoleCommand() {
        StaffAccount target = account(Role.ADMIN);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.changeRole(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(),
                new RoleChangeRequest(Role.DOCTOR, Role.ADMIN)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED));
    }

    @Test
    void updateWritesOneHistoryEntryForEachChangedField() {
        StaffAccount target = account(Role.RECEPTIONIST);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.updateAccount(admin, target.getId(), new UpdateAccountRequest(
                "Updated User", null, null, null, "0909876543", null, null, target.getVersion()));

        verify(auditLogService, times(2)).recordAccountChange(
                any(UUID.class), any(UUID.class), any(), anyMap(), anyMap(), any(Instant.class));
    }

    private StaffAccount account(Role role) {
        return StaffAccount.create(
                UUID.randomUUID(), role == Role.ADMIN ? "admin.seed" : "staff.user", "{bcrypt}hash",
                "Test User", null, "0901234567", null, role, NOW);
    }
}
