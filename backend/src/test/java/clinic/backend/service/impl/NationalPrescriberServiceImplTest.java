package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.NationalPrescriberRequest;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AccountAdministrationService;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.NationalPrescriptionSystem;
import clinic.backend.service.NationalPrescriptionSystem.Answer;
import clinic.backend.service.NationalPrescriptionSystem.SignIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** UC-93, BR-59-17: the Admin adds a Doctor to, or removes one from, the facility (QĐ 808 III, IV, V). */
@ExtendWith(MockitoExtension.class)
class NationalPrescriberServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Mock private StaffAccountRepository accountRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;
    @Mock private NationalPrescriptionSystem nationalSystem;
    @Mock private AccountAdministrationService accounts;
    @Mock private AuditLogService auditLogService;

    private NationalPrescriberServiceImpl service;
    private StaffAccount doctor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new NationalPrescriberServiceImpl(accountRepository, clinicFacilityRepository, nationalSystem,
                new NationalPrescriptionSessions(nationalSystem, clock), accounts, auditLogService, clock);
        doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        doctor.updateMaLienthongBacsi("BS0001", NOW);
        ReflectionTestUtils.setField(doctor, "version", 4L);
        lenient().when(accountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getMaLienthongDonthuoc()).thenReturn("LT79001");
        lenient().when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        lenient().when(nationalSystem.signInFacility(eq("LT79001"), any()))
                .thenReturn(new SignIn("token-cskcb", List.of()));
    }

    @Test
    void asksForTheFacilityPasswordWhenNoTokenIsKept() {
        assertError(() -> service.addToFacility(principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, null)),
                ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
        verify(nationalSystem, never()).addPrescriber(any(), any());
        assertThat(doctor.getLienthongAddedAt()).isNull();
    }

    @Test
    void addsTheDoctorAndRecordsWhenWithoutKeepingThePassword() {
        when(nationalSystem.addPrescriber("token-cskcb", "BS0001")).thenReturn(Answer.accepted("Bạn đã thêm bác sĩ thành công"));
        char[] password = "co-so".toCharArray();

        service.addToFacility(principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, password));

        assertThat(doctor.getLienthongAddedAt()).isEqualTo(NOW);
        assertThat(password).containsOnly('\0');
        verify(accountRepository).saveAndFlush(doctor);
        Map<String, Object> before = new java.util.HashMap<>();
        before.put("lienthongAddedAt", null);
        verify(auditLogService).recordAccountChange(eq(ADMIN_ID), eq(DOCTOR_ID), eq(AuditLogAction.UPDATE),
                eq(before), eq(Map.of("lienthongAddedAt", NOW.toString())), eq(NOW));
    }

    @Test
    void reusesTheFacilityTokenForTheNextDoctor() {
        when(nationalSystem.addPrescriber("token-cskcb", "BS0001")).thenReturn(Answer.accepted("ok"));
        service.addToFacility(principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, "co-so".toCharArray()));
        doctor.markRemovedFromNationalPrescriptionSystem(NOW);

        service.addToFacility(principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, null));

        verify(nationalSystem, times(1)).signInFacility(any(), any());
        verify(nationalSystem, times(2)).addPrescriber("token-cskcb", "BS0001");
    }

    @Test
    void reportsARefusalOfTheNationalSystemAndRecordsNothing() {
        when(nationalSystem.addPrescriber("token-cskcb", "BS0001"))
                .thenReturn(Answer.rejected("Lỗi", List.of("Mã liên thông bác sĩ không tồn tại.")));

        assertThatThrownBy(() -> service.addToFacility(
                principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, "co-so".toCharArray())))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NATIONAL_PRESCRIPTION_REQUEST_REJECTED);
                    assertThat(error.getDetails()).containsEntry("errors", "Mã liên thông bác sĩ không tồn tại.");
                });
        assertThat(doctor.getLienthongAddedAt()).isNull();
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void needsThePrescriberLinkCode() {
        doctor.updateMaLienthongBacsi(null, NOW);

        assertError(() -> service.addToFacility(
                        principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, "co-so".toCharArray())),
                ErrorCode.PRESCRIBER_LINK_CODE_REQUIRED);
    }

    @Test
    void refusesAStaleVersion() {
        assertError(() -> service.addToFacility(
                        principal(), DOCTOR_ID, new NationalPrescriberRequest(3L, "co-so".toCharArray())),
                ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void addingAnAddedDoctorSendsNothing() {
        doctor.markAddedToNationalPrescriptionSystem(NOW);

        service.addToFacility(principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, null));

        verify(nationalSystem, never()).signInFacility(any(), any());
        verify(nationalSystem, never()).addPrescriber(any(), any());
    }

    @Test
    void removesTheDoctor() {
        doctor.markAddedToNationalPrescriptionSystem(NOW);
        when(nationalSystem.removePrescriber("token-cskcb", "BS0001")).thenReturn(Answer.accepted("ok"));

        service.removeFromFacility(principal(), DOCTOR_ID, new NationalPrescriberRequest(4L, "co-so".toCharArray()));

        assertThat(doctor.getLienthongAddedAt()).isNull();
        verify(accountRepository).saveAndFlush(doctor);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(ADMIN_ID, UUID.randomUUID());
    }
}
