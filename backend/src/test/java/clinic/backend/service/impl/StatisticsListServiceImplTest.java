package clinic.backend.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse.DepartmentRow;
import clinic.backend.dto.response.DepartmentStatisticsResponse.RoomRow;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse.DoctorRow;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitCounts;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitPeriod;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsListServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Mock
    private StatisticsService statisticsService;

    private StatisticsListServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StatisticsListServiceImpl(statisticsService);
    }

    private static DoctorRow doctor(int index, long visits) {
        return new DoctorRow(UUID.randomUUID(), "Bác sĩ Nguyễn " + index, true, visits, visits, visits, 0, 0, 0);
    }

    private void doctors(List<DoctorRow> rows) {
        when(statisticsService.getDoctorStatistics(FROM, TO))
                .thenReturn(new DoctorStatisticsResponse(FROM, TO, 100, 3, rows));
    }

    @Test
    void sendsOnlyOnePageOfDoctorsWithTheTotal() {
        doctors(IntStream.rangeClosed(1, 45).mapToObj(i -> doctor(i, 50 - i)).toList());

        var page = service.getDoctorRows(FROM, TO, null, 2, 20);

        assertThat(page.content()).hasSize(5);
        assertThat(page.content().get(0).doctorName()).isEqualTo("Bác sĩ Nguyễn 41");
        assertThat(page.page().totalElements()).isEqualTo(45);
        assertThat(page.page().totalPages()).isEqualTo(3);
    }

    @Test
    void searchesDoctorsWithoutAccentsOnTheServer() {
        doctors(List.of(
                new DoctorRow(UUID.randomUUID(), "Trần Văn An", true, 3, 3, 3, 0, 0, 0),
                new DoctorRow(UUID.randomUUID(), "Lê Thị Bình", true, 1, 1, 1, 0, 0, 0)));

        var page = service.getDoctorRows(FROM, TO, "  TRAN van ", 0, 20);

        assertThat(page.content()).extracting(DoctorRow::doctorName).containsExactly("Trần Văn An");
        assertThat(page.page().totalElements()).isEqualTo(1);
    }

    @Test
    void doctorOverviewCarriesCountsAndOnlyTheTenBusiestForTheChart() {
        List<DoctorRow> rows = new java.util.ArrayList<>(
                IntStream.rangeClosed(1, 12).mapToObj(i -> doctor(i, 20 - i)).toList());
        rows.add(doctor(13, 0));
        doctors(rows);

        var overview = service.getDoctorOverview(FROM, TO);

        assertThat(overview.doctorCount()).isEqualTo(13);
        assertThat(overview.workingDoctors()).isEqualTo(12);
        assertThat(overview.topDoctors()).hasSize(10);
        assertThat(overview.examinedVisits()).isEqualTo(100);
    }

    @Test
    void visitRowsKeepOnlyPeriodsWithVisits() {
        VisitCounts none = new VisitCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);
        VisitCounts some = new VisitCounts(2, 2, 0, 0, 0, 1, 1, 0, 0);
        when(statisticsService.getVisitStatistics(FROM, TO, StatisticsGroupBy.DAY)).thenReturn(
                new VisitStatisticsResponse(FROM, TO, StatisticsGroupBy.DAY, some, List.of(
                        new VisitPeriod(FROM, none), new VisitPeriod(FROM.plusDays(1), some))));

        var page = service.getVisitRows(FROM, TO, StatisticsGroupBy.DAY, null, null);

        assertThat(page.content()).extracting(VisitPeriod::period).containsExactly(FROM.plusDays(1));
        assertThat(page.page().size()).isEqualTo(20);
    }

    @Test
    void filtersDepartmentsByTypeOnTheServer() {
        RoomRow room = new RoomRow(UUID.randomUUID(), "PK01", "Phòng 1", RoomType.EXAMINATION, true, 3, 2, 1, 0, 0, 0, 0);
        when(statisticsService.getDepartmentStatistics(FROM, TO)).thenReturn(new DepartmentStatisticsResponse(
                FROM, TO, 3, 0, List.of(
                        new DepartmentRow(UUID.randomUUID(), "NOI", "Khoa Nội", DepartmentType.CLINICAL, true,
                                3, 2, 0, 0, List.of(room)),
                        new DepartmentRow(UUID.randomUUID(), "XN", "Khoa Xét nghiệm", DepartmentType.PARACLINICAL,
                                true, 0, 0, 0, 0, List.of()))));

        var page = service.getDepartmentRows(FROM, TO, DepartmentType.PARACLINICAL, 0, 20);
        var overview = service.getDepartmentOverview(FROM, TO);

        assertThat(page.content()).extracting(DepartmentRow::departmentCode).containsExactly("XN");
        assertThat(overview.departmentCount()).isEqualTo(2);
        assertThat(overview.busyDepartments()).isEqualTo(1);
        assertThat(overview.busyRooms()).isEqualTo(1);
        assertThat(overview.roomCount()).isEqualTo(1);
        assertThat(overview.busy()).singleElement()
                .satisfies(load -> assertThat(load.departmentName()).isEqualTo("Khoa Nội"));
    }

    @Test
    void rejectsAPageSizeAboveTheLimit() {
        doctors(List.of());

        assertThatThrownBy(() -> service.getDoctorRows(FROM, TO, null, 0, 1000))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED));
    }
}
