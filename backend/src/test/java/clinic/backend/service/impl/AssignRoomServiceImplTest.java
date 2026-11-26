package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.AssignRoomRequest;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-15 Assign Staff to Room. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignRoomServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");
    private static final StaffPrincipal ADMIN = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

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
        when(clock.instant()).thenReturn(NOW);
        when(ids.next()).thenAnswer(invocation -> UUID.randomUUID());
    }

    @Test
    void assignsTechnicianToActiveParaclinicalRoomAndAuditsIt() {
        StaffAccount technician = account(Role.TECHNICIAN);
        Room room = room(RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technician.getId()))
                .thenReturn(Optional.empty());

        service.assignRoom(ADMIN, technician.getId(), new AssignRoomRequest(room.getId()));

        ArgumentCaptor<StaffRoomAssignment> saved = ArgumentCaptor.forClass(StaffRoomAssignment.class);
        verify(assignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getRoomId()).isEqualTo(room.getId());
        assertThat(saved.getValue().getAssignedByAccountId()).isEqualTo(ADMIN.accountId());
        assertThat(saved.getValue().getAssignedAt()).isEqualTo(NOW);
        java.util.Map<String, Object> oldValues = new java.util.HashMap<>();
        oldValues.put("roomId", null);
        verify(auditLogService).recordAccountChange(ADMIN.accountId(), technician.getId(), AuditLogAction.UPDATE,
                oldValues, Map.of("roomId", room.getId()), NOW);
    }

    @Test
    void movesDoctorToAnotherRoomAndClosesThePreviousAssignment() {
        StaffAccount doctor = account(Role.DOCTOR);
        Room oldRoom = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Room newRoom = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        StaffRoomAssignment current = new StaffRoomAssignment(UUID.randomUUID(), doctor.getId(), oldRoom.getId(),
                NOW.minusSeconds(86_400), UUID.randomUUID(), null, null, 0L);
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(doctor.getId()))
                .thenReturn(Optional.of(current));

        service.assignRoom(ADMIN, doctor.getId(), new AssignRoomRequest(newRoom.getId()));

        assertThat(current.getRemovedAt()).isEqualTo(NOW);
        assertThat(current.getRemovedByAccountId()).isEqualTo(ADMIN.accountId());
        // the old row must be closed in the database before the new active row is inserted
        InOrder order = inOrder(assignmentRepository);
        order.verify(assignmentRepository).saveAndFlush(current);
        order.verify(assignmentRepository).save(any());
        verify(auditLogService).recordAccountChange(ADMIN.accountId(), doctor.getId(), AuditLogAction.UPDATE,
                Map.of("roomId", oldRoom.getId()), Map.of("roomId", newRoom.getId()), NOW);
    }

    @Test
    void assigningTheSameRoomAgainChangesNothing() {
        StaffAccount doctor = account(Role.DOCTOR);
        Room room = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        StaffRoomAssignment current = new StaffRoomAssignment(UUID.randomUUID(), doctor.getId(), room.getId(),
                NOW.minusSeconds(86_400), UUID.randomUUID(), null, null, 0L);
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(doctor.getId()))
                .thenReturn(Optional.of(current));

        service.assignRoom(ADMIN, doctor.getId(), new AssignRoomRequest(room.getId()));

        assertThat(current.getRemovedAt()).isNull();
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsDoctorInAParaclinicalRoom() {
        StaffAccount doctor = account(Role.DOCTOR);
        Room room = room(RoomType.PARACLINICAL, ActivationStatus.ACTIVE);

        assertNotEligible(doctor, room);
    }

    @Test
    void rejectsTechnicianInAnExaminationRoom() {
        StaffAccount technician = account(Role.TECHNICIAN);
        Room room = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);

        assertNotEligible(technician, room);
    }

    @Test
    void rejectsAnInactiveRoom() {
        StaffAccount doctor = account(Role.DOCTOR);
        Room room = room(RoomType.EXAMINATION, ActivationStatus.INACTIVE);

        assertNotEligible(doctor, room);
    }

    @Test
    void rejectsARoleThatWorksWithoutARoom() {
        StaffAccount cashier = account(Role.CASHIER);
        Room room = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);

        assertThatThrownBy(() -> service.assignRoom(ADMIN, cashier.getId(), new AssignRoomRequest(room.getId())))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(e.getDetails()).containsEntry("role", "ROOM_NOT_APPLICABLE");
                });
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownRoom() {
        StaffAccount doctor = account(Role.DOCTOR);
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.assignRoom(ADMIN, doctor.getId(), new AssignRoomRequest(unknown)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void rejectsTheSeededAdminAccount() {
        StaffAccount admin = account(Role.ADMIN);
        Room room = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);

        assertThatThrownBy(() -> service.assignRoom(ADMIN, admin.getId(), new AssignRoomRequest(room.getId())))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED));
    }

    @Test
    void rejectsAssigningTheAdminsOwnAccount() {
        StaffAccount self = StaffAccount.create(ADMIN.accountId(), "staff.self", "{bcrypt}hash", "Self User", null,
                "0901234567", null, Role.DOCTOR, NOW);
        when(accountRepository.findById(self.getId())).thenReturn(Optional.of(self));
        Room room = room(RoomType.EXAMINATION, ActivationStatus.ACTIVE);

        assertThatThrownBy(() -> service.assignRoom(ADMIN, self.getId(), new AssignRoomRequest(room.getId())))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SELF_ADMINISTRATION_FORBIDDEN));
    }

    private void assertNotEligible(StaffAccount account, Room room) {
        assertThatThrownBy(() -> service.assignRoom(ADMIN, account.getId(), new AssignRoomRequest(room.getId())))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(e.getDetails()).containsEntry("roomId", "NOT_ELIGIBLE");
                });
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    private StaffAccount account(Role role) {
        StaffAccount account = StaffAccount.create(UUID.randomUUID(), role == Role.ADMIN ? "admin.seed" : "staff.user",
                "{bcrypt}hash", "Test User", null, "0901234567", null, role, NOW);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    private Room room(RoomType type, ActivationStatus status) {
        Room room = new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), null, "P" + type.ordinal(), "Phòng thử",
                "Tầng 1", type, status);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        return room;
    }
}
