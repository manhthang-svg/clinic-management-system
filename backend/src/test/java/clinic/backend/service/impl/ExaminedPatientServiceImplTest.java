package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.ExaminedPatientListResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-34 View Examined Patient List. */
@ExtendWith(MockitoExtension.class)
class ExaminedPatientServiceImplTest {

    /** 2026-11-14 09:00 in the clinic. */
    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");
    private static final StaffPrincipal DOCTOR = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Mock VisitRepository visitRepository;

    private ExaminedPatientServiceImpl service;

    record Totals(long getVisits, long getPatients) implements VisitRepository.ExaminedTotals { }

    record Row(UUID getVisitId, String getRegistrationCode, LocalDate getQueueDate, Instant getExaminedAt,
            VisitStatus getStatus, VisitDoituongbnLoai getPatientType, UUID getPatientId, String getPatientCode,
            String getPatientName, short getGender, LocalDate getDateOfBirth, String getRoomName, String getIcdCode,
            String getIcdName) implements VisitRepository.ExaminedVisitRow { }

    @BeforeEach
    void setUp() {
        service = new ExaminedPatientServiceImpl(visitRepository, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void listsTheVisitsOfTheLoggedInDoctorForTheCurrentMonthByDefault() {
        Row row = new Row(UUID.randomUUID(), "DK0001", LocalDate.of(2026, 11, 13), Instant.parse("2026-11-13T02:00:00Z"),
                VisitStatus.COMPLETED, VisitDoituongbnLoai.BHYT, UUID.randomUUID(), "BN0001", "Lê Văn Dũng", (short) 1,
                LocalDate.of(1958, 11, 2), "Phòng khám Nội 1", "J06.9", "Nhiễm trùng hô hấp trên cấp");
        LocalDate first = LocalDate.of(2026, 11, 1);
        LocalDate today = LocalDate.of(2026, 11, 14);
        when(visitRepository.countExamined(DOCTOR.accountId(), first, today)).thenReturn(new Totals(7, 5));
        when(visitRepository.findExaminedVisits(DOCTOR.accountId(), first, today, "", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ExaminedPatientListResponse result = service.getExaminedPatients(DOCTOR, null, null, null, null, null);

        assertThat(result.from()).isEqualTo(first);
        assertThat(result.to()).isEqualTo(today);
        assertThat(result.examinedVisits()).isEqualTo(7);
        assertThat(result.distinctPatients()).isEqualTo(5);
        // The totals cover the period; the page counts the rows of the list.
        assertThat(result.visits().page().totalElements()).isEqualTo(1);
        assertThat(result.visits().page().size()).isEqualTo(20);
        ExaminedPatientListResponse.ExaminedVisit visit = result.visits().content().get(0);
        assertThat(visit.patientName()).isEqualTo("Lê Văn Dũng");
        assertThat(visit.icdCode()).isEqualTo("J06.9");
        assertThat(visit.status()).isEqualTo(VisitStatus.COMPLETED);
    }

    @Test
    void passesTheTrimmedKeywordAndThePageAsked() {
        when(visitRepository.countExamined(any(), any(), any())).thenReturn(new Totals(0, 0));
        when(visitRepository.findExaminedVisits(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        service.getExaminedPatients(DOCTOR, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "  dung ", 2, 50);

        verify(visitRepository).findExaminedVisits(eq(DOCTOR.accountId()), eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 31)), eq("dung"), eq(PageRequest.of(2, 50)));
    }

    @Test
    void rejectsAnInvalidPeriodPageOrSize() {
        assertInvalid(() -> service.getExaminedPatients(
                DOCTOR, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null, null, null));
        assertInvalid(() -> service.getExaminedPatients(
                DOCTOR, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), null, null, null));
        assertInvalid(() -> service.getExaminedPatients(DOCTOR, null, null, null, -1, null));
        assertInvalid(() -> service.getExaminedPatients(DOCTOR, null, null, null, null, 0));
        assertInvalid(() -> service.getExaminedPatients(DOCTOR, null, null, null, null, 101));
        verifyNoInteractions(visitRepository);
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
    }

    @Test
    void rejectsAKeywordLongerThan100Characters() {
        assertThatThrownBy(() -> service.getExaminedPatients(DOCTOR, null, null, "a".repeat(101), null, null))
                .isInstanceOfSatisfying(clinic.backend.exceptions.AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);
                    assertThat(e.getDetails()).containsKey("keyword");
                });
    }
}
