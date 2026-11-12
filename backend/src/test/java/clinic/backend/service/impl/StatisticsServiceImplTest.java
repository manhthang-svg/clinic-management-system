package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse.DoctorRow;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitCounts;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.StatisticsGroupBy;
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

/** UC-26 View Statistics Dashboard, UC-27 View Patient Visit Statistics, UC-28 View Doctor Statistics. */
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

    record DoctorStatus(UUID getDoctorId, VisitStatus getStatus, long getTotal)
            implements StatisticsRepository.DoctorStatusCount { }

    record DoctorPatients(UUID getDoctorId, long getTotal) implements StatisticsRepository.DoctorPatientCount { }

    record Breakdown(LocalDate getDay, VisitStatus getStatus, VisitDoituongbnLoai getPatientType, boolean getPriority,
            long getTotal, long getFollowUps) implements StatisticsRepository.VisitBreakdown { }

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

    // ---- UC-27 View Patient Visit Statistics ----

    @Test
    void visitStatisticsDefaultToCurrentMonthByDay() {
        when(repository.visitBreakdown(any(), any())).thenReturn(List.of());

        VisitStatisticsResponse result = service.getVisitStatistics(null, null, null);

        assertThat(result.groupBy()).isEqualTo(StatisticsGroupBy.DAY);
        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(result.series()).hasSize(11).allSatisfy(p -> assertThat(p.counts().total()).isZero());
        assertThat(result.summary()).isEqualTo(new VisitCounts(0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void visitStatisticsCountOutcomesPatientTypesFollowUpsAndPriority() {
        LocalDate day1 = LocalDate.of(2026, 11, 2);
        LocalDate day2 = LocalDate.of(2026, 11, 3);
        when(repository.visitBreakdown(day1, day2)).thenReturn(List.of(
                new Breakdown(day1, VisitStatus.COMPLETED, VisitDoituongbnLoai.BHYT, false, 5, 2),
                new Breakdown(day1, VisitStatus.COMPLETED, VisitDoituongbnLoai.DV, true, 1, 0),
                new Breakdown(day1, VisitStatus.WAITING_PARACLINICAL, VisitDoituongbnLoai.DV, false, 2, 1),
                new Breakdown(day2, VisitStatus.CANCELLED, VisitDoituongbnLoai.DV, false, 3, 0),
                new Breakdown(day2, VisitStatus.ABANDONED, VisitDoituongbnLoai.BHYT, true, 4, 1)));

        VisitStatisticsResponse result = service.getVisitStatistics(day1, day2, StatisticsGroupBy.DAY);

        assertThat(result.summary()).isEqualTo(new VisitCounts(15, 6, 2, 3, 4, 9, 6, 4, 5));
        assertThat(result.series()).containsExactly(
                new VisitStatisticsResponse.VisitPeriod(day1, new VisitCounts(8, 6, 2, 0, 0, 5, 3, 3, 1)),
                new VisitStatisticsResponse.VisitPeriod(day2, new VisitCounts(7, 0, 0, 3, 4, 4, 3, 1, 4)));
    }

    @Test
    void visitStatisticsByMonthKeyEachBucketByItsFirstDay() {
        LocalDate from = LocalDate.of(2026, 9, 15);
        LocalDate to = LocalDate.of(2026, 11, 11);
        when(repository.visitBreakdown(from, to)).thenReturn(List.of(
                new Breakdown(LocalDate.of(2026, 9, 20), VisitStatus.COMPLETED, VisitDoituongbnLoai.DV, false, 2, 0),
                new Breakdown(LocalDate.of(2026, 9, 30), VisitStatus.COMPLETED, VisitDoituongbnLoai.DV, false, 3, 0),
                new Breakdown(LocalDate.of(2026, 11, 1), VisitStatus.WAITING, VisitDoituongbnLoai.BHYT, false, 4, 0)));

        VisitStatisticsResponse result = service.getVisitStatistics(from, to, StatisticsGroupBy.MONTH);

        assertThat(result.series()).extracting(VisitStatisticsResponse.VisitPeriod::period).containsExactly(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        assertThat(result.series()).extracting(p -> p.counts().total()).containsExactly(5L, 0L, 4L);
        assertThat(result.summary().total()).isEqualTo(9);
    }

    @Test
    void visitStatisticsRejectInvalidPeriods() {
        assertThatThrownBy(() -> service.getVisitStatistics(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), StatisticsGroupBy.DAY))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
        assertThatThrownBy(() -> service.getVisitStatistics(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), StatisticsGroupBy.MONTH))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(repository);
    }

    // ---- UC-28 View Doctor Statistics ----

    private static StaffAccount doctor(UUID id, String title, String name, AccountStatus status) {
        StaffAccount account = org.mockito.Mockito.mock(StaffAccount.class);
        lenient().when(account.getId()).thenReturn(id);
        lenient().when(account.getAcademicTitle()).thenReturn(title);
        lenient().when(account.getFullName()).thenReturn(name);
        lenient().when(account.getStatus()).thenReturn(status);
        return account;
    }

    @Test
    void doctorStatisticsCountEachDoctorsVisitsByOutcomeAndPatients() {
        UUID anId = UUID.randomUUID();
        UUID binhId = UUID.randomUUID();
        UUID idleId = UUID.randomUUID();
        StaffAccount an = doctor(anId, "BSCKII", "Trần Văn An", AccountStatus.ACTIVE);
        StaffAccount binh = doctor(binhId, null, "Lê Thị Bình", AccountStatus.ACTIVE);
        StaffAccount idle = doctor(idleId, "ThS", "Phạm Minh Châu", AccountStatus.ACTIVE);
        when(repository.countVisitsByDoctorAndStatus(any(), any())).thenReturn(List.of(
                new DoctorStatus(anId, VisitStatus.COMPLETED, 7),
                new DoctorStatus(anId, VisitStatus.WAITING_CONCLUSION, 2),
                new DoctorStatus(anId, VisitStatus.IN_EXAMINATION, 1),
                new DoctorStatus(anId, VisitStatus.ABANDONED, 1),
                new DoctorStatus(binhId, VisitStatus.COMPLETED, 4)));
        when(repository.countPatientsByDoctor(any(), any())).thenReturn(List.of(
                new DoctorPatients(anId, 10), new DoctorPatients(binhId, 4)));
        when(repository.countVisitsWithoutDoctor(any(), any())).thenReturn(3L);
        when(repository.doctorsOrAccounts(any())).thenReturn(List.of(idle, binh, an));

        DoctorStatisticsResponse result = service.getDoctorStatistics(null, null);

        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.examinedVisits()).isEqualTo(15);
        assertThat(result.visitsWithoutDoctor()).isEqualTo(3);
        assertThat(result.doctors()).containsExactly(
                new DoctorRow(anId, "BSCKII Trần Văn An", true, 11, 10, 7, 3, 1, 0),
                new DoctorRow(binhId, "Lê Thị Bình", true, 4, 4, 4, 0, 0, 0),
                new DoctorRow(idleId, "ThS Phạm Minh Châu", true, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void doctorStatisticsKeepAnInactiveDoctorOnlyWhenTheyExaminedPatients() {
        UUID leftId = UUID.randomUUID();
        StaffAccount left = doctor(leftId, null, "Đỗ Văn Dũng", AccountStatus.LOCKED);
        StaffAccount gone = doctor(UUID.randomUUID(), null, "Hồ Thị Em", AccountStatus.LOCKED);
        when(repository.countVisitsByDoctorAndStatus(any(), any())).thenReturn(List.of(
                new DoctorStatus(leftId, VisitStatus.COMPLETED, 2)));
        when(repository.countPatientsByDoctor(any(), any())).thenReturn(List.of(new DoctorPatients(leftId, 2)));
        when(repository.doctorsOrAccounts(any())).thenReturn(List.of(left, gone));

        DoctorStatisticsResponse result = service.getDoctorStatistics(null, null);

        assertThat(result.doctors()).extracting(DoctorRow::doctorName).containsExactly("Đỗ Văn Dũng");
        assertThat(result.doctors().get(0).active()).isFalse();
    }

    @Test
    void doctorStatisticsRejectInvalidPeriods() {
        assertThatThrownBy(() -> service.getDoctorStatistics(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
        verifyNoInteractions(repository);
    }
}
