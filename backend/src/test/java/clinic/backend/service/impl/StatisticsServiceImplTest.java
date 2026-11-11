package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.StatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-26 View Statistics Dashboard. */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-11-11 08:00 in the clinic. */
    private static final Instant NOW = Instant.parse("2026-11-11T01:00:00Z");

    @Mock StatisticsRepository repository;

    private StatisticsServiceImpl service;

    record Status(VisitStatus getStatus, long getTotal) implements StatisticsRepository.StatusCount { }

    record Type(VisitDoituongbnLoai getPatientType, long getTotal) implements StatisticsRepository.PatientTypeCount { }

    record Day(LocalDate getDay, long getTotal) implements StatisticsRepository.DayCount { }

    record RoomRow(UUID getRoomId, String getRoomName, long getTotal) implements StatisticsRepository.RoomCount { }

    record Diagnosis(String getCode, String getName, long getTotal) implements StatisticsRepository.DiagnosisCount { }

    record Revenue(long getPaymentCount, BigDecimal getTotalAmount, BigDecimal getInsuranceAmount,
            BigDecimal getPatientAmount) implements StatisticsRepository.RevenueTotals { }

    @BeforeEach
    void setUp() {
        service = new StatisticsServiceImpl(repository, Clock.fixed(NOW, CLINIC_ZONE));
    }

    private void stubEmpty() {
        lenient().when(repository.countVisitsByStatus(any(), any())).thenReturn(List.of());
        lenient().when(repository.countVisitsByPatientType(any(), any())).thenReturn(List.of());
        lenient().when(repository.countVisitsByDay(any(), any())).thenReturn(List.of());
        lenient().when(repository.topRoomsByVisits(any(), any(), any())).thenReturn(List.of());
        lenient().when(repository.topDiagnoses(any(), any(), any())).thenReturn(List.of());
        lenient().when(repository.sumRevenue(any(), any(), any()))
                .thenReturn(new Revenue(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void defaultsToFirstDayOfMonthUntilTodayInClinicZone() {
        stubEmpty();

        DashboardStatisticsResponse result = service.getDashboard(null, null);

        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(result.dailyVisits()).hasSize(11);
    }

    @Test
    void usesClinicDayBoundariesForTimestampedData() {
        stubEmpty();
        LocalDate day = LocalDate.of(2026, 11, 5);

        service.getDashboard(day, day);

        Instant start = Instant.parse("2026-11-04T17:00:00Z");
        Instant end = Instant.parse("2026-11-05T17:00:00Z");
        verify(repository).sumRevenue(PaymentStatus.PAID, start, end);
        verify(repository).countParaclinicalServices(ParaclinicalOrderDetailStatus.COMPLETED, start, end);
        verify(repository).topDiagnoses(eq(start), eq(end), any(Pageable.class));
    }

    @Test
    void groupsVisitStatusesAndPatientTypes() {
        stubEmpty();
        when(repository.countVisitsByStatus(any(), any())).thenReturn(List.of(
                new Status(VisitStatus.COMPLETED, 10),
                new Status(VisitStatus.WAITING, 2),
                new Status(VisitStatus.IN_EXAMINATION, 1),
                new Status(VisitStatus.WAITING_CONCLUSION, 3),
                new Status(VisitStatus.CANCELLED, 4),
                new Status(VisitStatus.ABANDONED, 5)));
        when(repository.countVisitsByPatientType(any(), any())).thenReturn(List.of(
                new Type(VisitDoituongbnLoai.BHYT, 15),
                new Type(VisitDoituongbnLoai.DV, 10)));
        when(repository.countDistinctPatients(any(), any())).thenReturn(20L);

        DashboardStatisticsResponse result = service.getDashboard(null, null);

        assertThat(result.visits()).isEqualTo(
                new DashboardStatisticsResponse.VisitSummary(25, 10, 6, 4, 5, 15, 10));
        assertThat(result.distinctPatients()).isEqualTo(20);
    }

    @Test
    void keepsInsuranceAndPatientRevenueSeparate() {
        stubEmpty();
        when(repository.sumRevenue(any(), any(), any())).thenReturn(new Revenue(
                3, new BigDecimal("500000"), new BigDecimal("320000"), new BigDecimal("180000")));

        DashboardStatisticsResponse.RevenueSummary revenue = service.getDashboard(null, null).revenue();

        assertThat(revenue.paymentCount()).isEqualTo(3);
        assertThat(revenue.totalAmount()).isEqualByComparingTo("500000");
        assertThat(revenue.insuranceAmount()).isEqualByComparingTo("320000");
        assertThat(revenue.patientAmount()).isEqualByComparingTo("180000");
    }

    @Test
    void fillsDaysWithoutVisitsWithZero() {
        stubEmpty();
        LocalDate from = LocalDate.of(2026, 11, 1);
        when(repository.countVisitsByDay(any(), any())).thenReturn(List.of(
                new Day(LocalDate.of(2026, 11, 2), 7)));

        DashboardStatisticsResponse result = service.getDashboard(from, LocalDate.of(2026, 11, 3));

        assertThat(result.dailyVisits()).containsExactly(
                new DashboardStatisticsResponse.DailyVisits(LocalDate.of(2026, 11, 1), 0),
                new DashboardStatisticsResponse.DailyVisits(LocalDate.of(2026, 11, 2), 7),
                new DashboardStatisticsResponse.DailyVisits(LocalDate.of(2026, 11, 3), 0));
    }

    @Test
    void mapsTopRoomsAndDiagnosesLimitedToFive() {
        stubEmpty();
        UUID roomId = UUID.randomUUID();
        when(repository.topRoomsByVisits(any(), any(), any()))
                .thenReturn(List.of(new RoomRow(roomId, "Phòng khám Nội 1", 12)));
        when(repository.topDiagnoses(any(), any(), any()))
                .thenReturn(List.of(new Diagnosis("J06.9", "Nhiễm trùng hô hấp trên cấp", 8)));

        DashboardStatisticsResponse result = service.getDashboard(null, null);

        assertThat(result.topRooms()).containsExactly(
                new DashboardStatisticsResponse.RoomVisits(roomId, "Phòng khám Nội 1", 12));
        assertThat(result.topDiagnoses()).containsExactly(
                new DashboardStatisticsResponse.DiagnosisCount("J06.9", "Nhiễm trùng hô hấp trên cấp", 8));
        verify(repository).topRoomsByVisits(any(), any(), eq(Pageable.ofSize(StatisticsServiceImpl.TOP_LIMIT)));
    }

    @Test
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> service.getDashboard(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsPeriodLongerThanOneYear() {
        assertThatThrownBy(() -> service.getDashboard(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsExactlyOneLeapYear() {
        stubEmpty();

        DashboardStatisticsResponse result =
                service.getDashboard(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));

        assertThat(result.dailyVisits()).hasSize(366);
    }
}
