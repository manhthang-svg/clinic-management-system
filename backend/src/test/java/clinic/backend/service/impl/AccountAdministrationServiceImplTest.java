package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.ResetAccountPasswordRequest;
import clinic.backend.dto.request.AssignRoomRequest;
import clinic.backend.dto.request.CreateAccountRequest;
import clinic.backend.dto.request.LockAccountRequest;
import clinic.backend.dto.request.RoleChangeRequest;
import clinic.backend.dto.request.UpdateAccountRequest;
import clinic.backend.dto.response.AccountSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.TemporaryPasswordMode;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.AccountMapper;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.DutyScheduleRepository;
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
import clinic.backend.service.AuditLogService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdministrationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    @Mock StaffAccountRepository accountRepository;
    @Mock StaffRoomAssignmentRepository assignmentRepository;
    @Mock DutyScheduleRepository dutyScheduleRepository;
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
                accountRepository, assignmentRepository, dutyScheduleRepository, roomRepository, departmentRepository, sessionRepository,
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
                new RoleChangeRequest(Role.DOCTOR, Role.RECEPTIONIST, "ThS.BS", "000123/HCM-CCHN")))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WORK_IN_PROGRESS));
    }

    @Test
    void roleChangeToDoctorRequiresTheAcademicTitleAndPracticeLicense() {
        StaffAccount target = account(Role.RECEPTIONIST);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.changeRole(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(),
                new RoleChangeRequest(Role.DOCTOR, Role.RECEPTIONIST, "  ", "000123/HCM-CCHN")))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsEntry("academicTitle", "REQUIRED");
                });
        assertThatThrownBy(() -> service.checkRoleChange(
                target.getId(), new RoleChangeRequest(Role.TECHNICIAN, Role.RECEPTIONIST, "KTV", null)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("practiceLicenseNo", "REQUIRED"));
        assertThat(target.getRole()).isEqualTo(Role.RECEPTIONIST);
        verifyNoInteractions(sessionRepository, auditLogService);
    }

    @Test
    void roleChangeToDoctorRejectsALicenseUsedByAnotherAccount() {
        StaffAccount target = account(Role.RECEPTIONIST);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(accountRepository.existsByPracticeLicenseNoAndIdNot("000123/HCM-CCHN", target.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.changeRole(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(),
                new RoleChangeRequest(Role.DOCTOR, Role.RECEPTIONIST, "ThS.BS", " 000123/HCM-CCHN ")))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("practiceLicenseNo", "ALREADY_EXISTS"));
        assertThat(target.getRole()).isEqualTo(Role.RECEPTIONIST);
        verifyNoInteractions(sessionRepository, auditLogService);
    }

    @Test
    void roleChangeToDoctorSavesTheProfessionalInformationWithTheRole() {
        StaffAccount target = account(Role.RECEPTIONIST);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.changeRole(admin, target.getId(),
                new RoleChangeRequest(Role.DOCTOR, Role.RECEPTIONIST, " ThS.BS ", "000123/HCM-CCHN"));

        assertThat(target.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(target.getAcademicTitle()).isEqualTo("ThS.BS");
        assertThat(target.getPracticeLicenseNo()).isEqualTo("000123/HCM-CCHN");
        var newValues = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditLogService).recordAccountChange(
                eq(admin.accountId()), eq(target.getId()), any(), anyMap(), newValues.capture(), any(Instant.class));
        assertThat(newValues.getValue()).containsEntry("role", Role.DOCTOR)
                .containsEntry("academicTitle", "ThS.BS")
                .containsEntry("practiceLicenseNo", "000123/HCM-CCHN");
    }

    @Test
    void roleChangeToCashierIgnoresProfessionalInformation() {
        StaffAccount target = account(Role.RECEPTIONIST);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.changeRole(new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(),
                new RoleChangeRequest(Role.CASHIER, Role.RECEPTIONIST, null, null));

        assertThat(target.getRole()).isEqualTo(Role.CASHIER);
        assertThat(target.getPracticeLicenseNo()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(TemporaryPasswordMode.class)
    void creationRequiresPasswordChangeOnlyForGeneratedPasswords(TemporaryPasswordMode mode) {
        String password = "InitialPass1";
        when(ids.next()).thenReturn(UUID.randomUUID());
        when(clock.instant()).thenReturn(NOW);
        when(passwordHasher.hash(password)).thenReturn("{bcrypt}test-hash");
        if (mode == TemporaryPasswordMode.GENERATE) {
            when(passwordGenerator.generate()).thenReturn(password);
        }
        service.createAccount(new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                new CreateAccountRequest("staff.new", "New Staff", null, null, null, null,
                        "0901234567", null, null, Role.RECEPTIONIST, mode,
                        mode == TemporaryPasswordMode.MANUAL ? password : null, UUID.randomUUID()));

        var saved = org.mockito.ArgumentCaptor.forClass(StaffAccount.class);
        verify(accountRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPasswordChangeRequired())
                .isEqualTo(mode == TemporaryPasswordMode.GENERATE);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}test-hash");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void creationRejectsStaffYoungerThanEighteen() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));

        assertThatThrownBy(() -> service.createAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                new CreateAccountRequest("staff.young", "Young Staff", null, null,
                        LocalDate.of(2009, 10, 24), null, "0901234567", null, null,
                        Role.RECEPTIONIST, TemporaryPasswordMode.MANUAL, "InitialPass1", UUID.randomUUID())))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsEntry("dateOfBirth", "INVALID_FORMAT");
                });
    }

    @Test
    void creationAcceptsStaffOnTheirEighteenthBirthday() {
        when(ids.next()).thenReturn(UUID.randomUUID());
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(passwordHasher.hash("InitialPass1")).thenReturn("{bcrypt}test-hash");

        service.createAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                new CreateAccountRequest("staff.adult", "Adult Staff", null, null,
                        LocalDate.of(2008, 10, 23), null, "0901234567", null, null,
                        Role.RECEPTIONIST, TemporaryPasswordMode.MANUAL, "InitialPass1", UUID.randomUUID()));

        verify(accountRepository).saveAndFlush(any(StaffAccount.class));
    }

    @Test
    void creationRejectsInvalidPracticeLicenseFormat() {
        assertThatThrownBy(() -> service.createAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                new CreateAccountRequest("doctor.new", "New Doctor", "BS", "AB 12",
                        null, null, "0901234567", null, null, Role.DOCTOR,
                        TemporaryPasswordMode.MANUAL, "InitialPass1", UUID.randomUUID())))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsEntry("practiceLicenseNo", "INVALID_FORMAT");
                });
    }

    @Test
    void lockIsRejectedWhileTheTargetHasAVisitInExamination() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(visitRepository.countByDoctorAccountIdAndStatus(target.getId(), VisitStatus.IN_EXAMINATION))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.lock(admin, target.getId(),
                new LockAccountRequest("Đang nghỉ việc", AccountStatus.ACTIVE, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WORK_IN_PROGRESS));
    }

    @Test
    void lockRejectsAReasonShorterThanFiveCharactersAfterTrimming() {
        StaffAccount target = account(Role.RECEPTIONIST);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.lock(admin, target.getId(),
                new LockAccountRequest("  abc   ", AccountStatus.ACTIVE, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsEntry("reason", "TOO_SHORT");
                });
        verifyNoInteractions(accountRepository, sessionRepository, auditLogService, securityAuditService);
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
    void doctorCanBeAssignedToAnActiveExaminationRoom() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        UUID roomId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Room room = new Room(roomId, NOW, UUID.randomUUID(), null, "P101", "Phòng 101", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(target.getId()))
                .thenReturn(Optional.empty());
        when(ids.next()).thenReturn(assignmentId);
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.assignRoom(admin, target.getId(), new AssignRoomRequest(roomId));

        verify(assignmentRepository).save(org.mockito.ArgumentMatchers.argThat((StaffRoomAssignment assignment) ->
                assignment.getId().equals(assignmentId)
                        && assignment.getStaffAccountId().equals(target.getId())
                        && assignment.getRoomId().equals(roomId)));
    }

    @Test
    void staffWithADutyShiftTodayOrLaterCannotChangeRoom() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        UUID currentRoomId = UUID.randomUUID();
        UUID newRoomId = UUID.randomUUID();
        Room newRoom = new Room(newRoomId, NOW, UUID.randomUUID(), null, "P102", "Phòng 102", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        StaffRoomAssignment current = new StaffRoomAssignment(
                UUID.randomUUID(), target.getId(), currentRoomId, NOW, admin.accountId(), null, null, null);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(roomRepository.findById(newRoomId)).thenReturn(Optional.of(newRoom));
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(target.getId()))
                .thenReturn(Optional.of(current));
        // 2026-10-23T00:00Z is 07:00 on 23/10 in clinic time.
        when(clock.instant()).thenReturn(NOW);
        when(dutyScheduleRepository.countByStaffAccountIdAndWorkDateGreaterThanEqual(
                target.getId(), LocalDate.of(2026, 10, 23))).thenReturn(3L);

        assertThatThrownBy(() -> service.assignRoom(admin, target.getId(), new AssignRoomRequest(newRoomId)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STAFF_HAS_DUTY_SCHEDULES);
                            assertThat(exception.getDetails()).containsEntry("shiftCount", "3");
                        });
        assertThat(current.getRemovedAt()).isNull();
        verify(assignmentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateWritesOneHistoryEntryForEachChangedField() {
        StaffAccount target = account(Role.RECEPTIONIST);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.updateAccount(admin, target.getId(), new UpdateAccountRequest(
                "Updated User", null, null, null, null, null, null, "0909876543", null, null, target.getVersion()));

        verify(auditLogService, times(2)).recordAccountChange(
                any(UUID.class), any(UUID.class), any(), anyMap(), anyMap(), any(Instant.class));
    }

    @Test
    void doctorPracticeLicenseIsUpdatedAndWrittenToHistory() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.updateAccount(admin, target.getId(), new UpdateAccountRequest(
                "Test User", "Bác sĩ", "000123/HCM-CCHN", null, null, null, null,
                "0901234567", null, null, target.getVersion()));

        assertThat(target.getPracticeLicenseNo()).isEqualTo("000123/HCM-CCHN");
        verify(auditLogService, times(2)).recordAccountChange(
                any(UUID.class), any(UUID.class), any(), anyMap(), anyMap(), any(Instant.class));
    }

    @Test
    void doctorPrescriberLinkCodeIsSavedAndWrittenToHistory() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.updateAccount(admin, target.getId(), new UpdateAccountRequest(
                "Test User", "Bác sĩ", "000123/HCM-CCHN", " BS0001 ", null, null, null,
                "0901234567", null, null, target.getVersion()));

        assertThat(target.getMaLienthongBacsi()).isEqualTo("BS0001");
        verify(auditLogService, times(3)).recordAccountChange(
                any(UUID.class), any(UUID.class), any(), anyMap(), anyMap(), any(Instant.class));
    }

    @Test
    void prescriberLinkCodeIsRejectedForANonDoctor() {
        StaffAccount target = account(Role.TECHNICIAN);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(), new UpdateAccountRequest(
                        "Test User", "KTV", "000123/HCM-CCHN", "BS0001", null, null, null,
                        "0901234567", null, null, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("maLienthongBacsi", "NOT_APPLICABLE"));
        assertThat(target.getMaLienthongBacsi()).isNull();
    }

    @Test
    void prescriberLinkCodeUsedByAnotherAccountIsRejected() {
        StaffAccount target = account(Role.DOCTOR);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(accountRepository.existsByMaLienthongBacsiAndIdNot("BS0001", target.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.updateAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(), new UpdateAccountRequest(
                        "Test User", "Bác sĩ", "000123/HCM-CCHN", "BS0001", null, null, null,
                        "0901234567", null, null, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("maLienthongBacsi", "ALREADY_EXISTS"));
        assertThat(target.getMaLienthongBacsi()).isNull();
    }

    @Test
    void doctorCitizenIdIsSavedAndTheAuditRowHidesTheNumber() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.updateAccount(admin, target.getId(), new UpdateAccountRequest(
                "Test User", "Bác sĩ", "000123/HCM-CCHN", null, " 079090001234 ", null, null,
                "0901234567", null, null, target.getVersion()));

        assertThat(target.getCitizenId()).isEqualTo("079090001234");
        verify(auditLogService).recordAccountChange(
                eq(admin.accountId()), eq(target.getId()), eq(AuditLogAction.UPDATE),
                eq(java.util.Collections.singletonMap("citizenId", null)), eq(Map.of("citizenId", "***")),
                any(Instant.class));
    }

    @Test
    void citizenIdIsRejectedForANonDoctor() {
        StaffAccount target = account(Role.TECHNICIAN);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(), new UpdateAccountRequest(
                        "Test User", "KTV", "000123/HCM-CCHN", null, "079090001234", null, null,
                        "0901234567", null, null, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("citizenId", "NOT_APPLICABLE"));
        assertThat(target.getCitizenId()).isNull();
    }

    @Test
    void citizenIdUsedByAnotherAccountIsRejected() {
        StaffAccount target = account(Role.DOCTOR);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(accountRepository.existsByCitizenIdAndIdNot("079090001234", target.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.updateAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(), new UpdateAccountRequest(
                        "Test User", "Bác sĩ", "000123/HCM-CCHN", null, "079090001234", null, null,
                        "0901234567", null, null, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("citizenId", "ALREADY_EXISTS"));
    }

    @Test
    void citizenIdThatIsNotTwelveDigitsIsRejected() {
        StaffAccount target = account(Role.DOCTOR);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(), new UpdateAccountRequest(
                        "Test User", "Bác sĩ", "000123/HCM-CCHN", null, "07909000123", null, null,
                        "0901234567", null, null, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("citizenId", "INVALID_FORMAT"));
    }

    @Test
    void prescriberLinkCodeWithSymbolsIsRejected() {
        StaffAccount target = account(Role.DOCTOR);
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateAccount(
                new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID()), target.getId(), new UpdateAccountRequest(
                        "Test User", "Bác sĩ", "000123/HCM-CCHN", "BS-0001", null, null, null,
                        "0901234567", null, null, target.getVersion())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("maLienthongBacsi", "INVALID_FORMAT"));
    }

    @Test
    void accountListUsesOneProjectedPageQuery() {
        StaffAccount doctor = account(Role.DOCTOR);
        StaffAccount receptionist = account(Role.RECEPTIONIST);
        AccountSummaryView doctorView = summaryView(doctor, "Phòng 101", "Khoa Nội");
        AccountSummaryView receptionistView = summaryView(receptionist, null, null);
        when(clock.instant()).thenReturn(NOW);
        when(accountRepository.searchSummaries(eq(""), isNull(), isNull(), isNull(),
                eq(LocalDate.of(2026, 10, 23)), any()))
                .thenReturn(new PageImpl<>(List.of(doctorView, receptionistView)));

        PageResponse<AccountSummaryResponse> result = service.listAccounts(
                null, null, null, null, 0, 20, "createdAt", true);

        assertThat(result.content()).extracting(AccountSummaryResponse::roomName)
                .containsExactly("Phòng 101", null);
        verify(accountRepository).searchSummaries(eq(""), isNull(), isNull(), isNull(),
                eq(LocalDate.of(2026, 10, 23)), any());
        verifyNoInteractions(assignmentRepository);
    }

    private AccountSummaryView summaryView(StaffAccount account, String roomName, String departmentName) {
        AccountSummaryView view = org.mockito.Mockito.mock(AccountSummaryView.class);
        when(view.getId()).thenReturn(account.getId());
        when(view.getUsername()).thenReturn(account.getUsername());
        when(view.getFullName()).thenReturn(account.getFullName());
        when(view.getPhoneNumber()).thenReturn(account.getPhoneNumber());
        when(view.getEmail()).thenReturn(account.getEmail());
        when(view.getRole()).thenReturn(account.getRole());
        when(view.getStatus()).thenReturn(account.getStatus());
        when(view.getRoomName()).thenReturn(roomName);
        when(view.getDepartmentName()).thenReturn(departmentName);
        when(view.getLastLoginAt()).thenReturn(account.getLastLoginAt());
        when(view.getPasswordChangeRequired()).thenReturn(account.isPasswordChangeRequired());
        when(view.getVersion()).thenReturn(account.getVersion());
        return view;
    }

    @Test
    void generatedPasswordResetRequiresPasswordChange() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(passwordGenerator.generate()).thenReturn("GeneratedPass1");
        when(passwordHasher.hash("GeneratedPass1")).thenReturn("{bcrypt}generated");
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.resetPassword(admin, target.getId(),
                new ResetAccountPasswordRequest(TemporaryPasswordMode.GENERATE, null, target.getVersion()));

        assertThat(target.getPasswordHash()).isEqualTo("{bcrypt}generated");
        assertThat(target.isPasswordChangeRequired()).isTrue();
    }

    @Test
    void manualPasswordResetDoesNotRequirePasswordChange() {
        StaffAccount target = account(Role.DOCTOR);
        StaffPrincipal admin = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(passwordHasher.hash("AgreedPassword1")).thenReturn("{bcrypt}agreed");
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.resetPassword(admin, target.getId(),
                new ResetAccountPasswordRequest(
                        TemporaryPasswordMode.MANUAL, "AgreedPassword1", target.getVersion()));

        assertThat(target.getPasswordHash()).isEqualTo("{bcrypt}agreed");
        assertThat(target.isPasswordChangeRequired()).isFalse();
    }

    private StaffAccount account(Role role) {
        return StaffAccount.create(
                UUID.randomUUID(), role == Role.ADMIN ? "admin.seed" : "staff.user", "{bcrypt}hash",
                "Test User", null, "0901234567", null, role, NOW);
    }
}
