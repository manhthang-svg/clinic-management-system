package clinic.backend.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import clinic.backend.dto.response.DepartmentStatisticsOverviewResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse.DepartmentRow;
import clinic.backend.dto.response.DepartmentStatisticsResponse.RoomRow;
import clinic.backend.dto.response.DiagnosisStatisticsOverviewResponse;
import clinic.backend.dto.response.DiagnosisStatisticsResponse;
import clinic.backend.dto.response.DiagnosisStatisticsResponse.CategoryRow;
import clinic.backend.dto.response.DiagnosisStatisticsResponse.DiagnosisRow;
import clinic.backend.dto.response.DiagnosisStatisticsResponse.SecondaryRow;
import clinic.backend.dto.response.DoctorStatisticsOverviewResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse.DoctorRow;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsOverviewResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse.ServiceCount;
import clinic.backend.dto.response.RevenueStatisticsResponse.PeriodAmounts;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitPeriod;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.service.StatisticsListService;
import clinic.backend.service.StatisticsService;
import clinic.backend.utils.VietnameseText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// trang thong ke (UC-27 den UC-32): so lieu tong + bieu do, bang loc va cat trang o server
@Service
@RequiredArgsConstructor
public class StatisticsListServiceImpl implements StatisticsListService {

    // so dong tren bieu do (bac si, phong, benh)
    static final int CHART_SIZE = 10;

    private final StatisticsService statisticsService;

    // UC-27: chi cac ngay / thang co luot kham
    @Override
    public PageResponse<VisitPeriod> getVisitRows(
            LocalDate from, LocalDate to, StatisticsGroupBy groupBy, Integer page, Integer size) {
        List<VisitPeriod> rows = statisticsService.getVisitStatistics(from, to, groupBy).series().stream()
                .filter(row -> row.counts().total() > 0)
                .toList();
        return ListPage.of(rows, page, size);
    }

    // UC-31: chi cac ngay / thang co khoan thu
    @Override
    public PageResponse<PeriodAmounts> getRevenueRows(
            LocalDate from, LocalDate to, StatisticsGroupBy groupBy, Integer page, Integer size) {
        List<PeriodAmounts> rows = statisticsService.getRevenueStatistics(from, to, groupBy).series().stream()
                .filter(row -> row.amounts().payments() > 0)
                .toList();
        return ListPage.of(rows, page, size);
    }

    // UC-28: the + bieu do 10 bac si kham nhieu nhat (danh sach da xep nhieu luot nhat truoc)
    @Override
    public DoctorStatisticsOverviewResponse getDoctorOverview(LocalDate from, LocalDate to) {
        DoctorStatisticsResponse stats = statisticsService.getDoctorStatistics(from, to);
        List<DoctorRow> working = stats.doctors().stream().filter(doctor -> doctor.visits() > 0).toList();
        return new DoctorStatisticsOverviewResponse(stats.from(), stats.to(), stats.examinedVisits(),
                stats.visitsWithoutDoctor(), stats.doctors().size(), working.size(), first(working, CHART_SIZE));
    }

    // UC-28: bang bac si, tim theo ten (khong dau, khong phan biet hoa thuong)
    @Override
    public PageResponse<DoctorRow> getDoctorRows(
            LocalDate from, LocalDate to, String search, Integer page, Integer size) {
        List<DoctorRow> rows = matching(statisticsService.getDoctorStatistics(from, to).doctors(), search,
                List.of(DoctorRow::doctorName));
        return ListPage.of(rows, page, size);
    }

    // UC-29: the + bieu do cac khoa co cong viec
    @Override
    public DepartmentStatisticsOverviewResponse getDepartmentOverview(LocalDate from, LocalDate to) {
        DepartmentStatisticsResponse stats = statisticsService.getDepartmentStatistics(from, to);
        List<DepartmentRow> busy = stats.departments().stream()
                .filter(department -> department.visits() + department.services() > 0)
                .toList();
        List<RoomRow> rooms = stats.departments().stream()
                .flatMap(department -> department.rooms().stream())
                .toList();
        long busyRooms = rooms.stream().filter(room -> room.visits() + room.services() > 0).count();
        List<DepartmentStatisticsOverviewResponse.DepartmentLoad> chart = busy.stream()
                .map(department -> new DepartmentStatisticsOverviewResponse.DepartmentLoad(
                        department.departmentName(), department.visits(), department.services()))
                .toList();
        return new DepartmentStatisticsOverviewResponse(stats.from(), stats.to(), stats.visits(), stats.services(),
                stats.departments().size(), busy.size(), busyRooms, rooms.size(), chart);
    }

    // UC-29: bang khoa (kem phong cua khoa), loc theo loai khoa
    @Override
    public PageResponse<DepartmentRow> getDepartmentRows(
            LocalDate from, LocalDate to, DepartmentType type, Integer page, Integer size) {
        List<DepartmentRow> rows = statisticsService.getDepartmentStatistics(from, to).departments().stream()
                .filter(department -> type == null || department.type() == type)
                .toList();
        return ListPage.of(rows, page, size);
    }

    // UC-30: the, loai, nhom, bieu do 10 phong lam nhieu nhat
    @Override
    public ParaclinicalStatisticsOverviewResponse getParaclinicalOverview(LocalDate from, LocalDate to) {
        ParaclinicalStatisticsResponse stats = statisticsService.getParaclinicalStatistics(from, to);
        return new ParaclinicalStatisticsOverviewResponse(stats.from(), stats.to(), stats.performed(),
                stats.withResult(), stats.byType(), stats.byGroup(), stats.byService().size(), stats.byRoom().size(),
                first(stats.byRoom(), CHART_SIZE));
    }

    // UC-30: bang dich vu, tim theo ten hoac ma
    @Override
    public PageResponse<ServiceCount> getParaclinicalServiceRows(
            LocalDate from, LocalDate to, String search, Integer page, Integer size) {
        List<ServiceCount> rows = matching(statisticsService.getParaclinicalStatistics(from, to).byService(), search,
                List.of(ServiceCount::serviceName, ServiceCount::serviceCode));
        return ListPage.of(rows, page, size);
    }

    // UC-32: the + bieu do 10 benh chinh gap nhieu nhat
    @Override
    public DiagnosisStatisticsOverviewResponse getDiagnosisOverview(LocalDate from, LocalDate to, Integer limit) {
        DiagnosisStatisticsResponse stats = statisticsService.getDiagnosisStatistics(from, to, limit);
        return new DiagnosisStatisticsOverviewResponse(stats.from(), stats.to(), stats.limit(), stats.conclusions(),
                stats.withIcd(), stats.withoutIcd(), stats.distinctCodes(), first(stats.mainDiagnoses(), CHART_SIZE));
    }

    // UC-32: bang benh chinh (top N da chon)
    @Override
    public PageResponse<DiagnosisRow> getMainDiagnosisRows(
            LocalDate from, LocalDate to, Integer limit, Integer page, Integer size) {
        return ListPage.of(statisticsService.getDiagnosisStatistics(from, to, limit).mainDiagnoses(), page, size);
    }

    // UC-32: nhom benh (3 ky tu dau cua ma)
    @Override
    public PageResponse<CategoryRow> getDiagnosisCategoryRows(
            LocalDate from, LocalDate to, Integer limit, Integer page, Integer size) {
        return ListPage.of(statisticsService.getDiagnosisStatistics(from, to, limit).categories(), page, size);
    }

    // UC-32: benh kem theo
    @Override
    public PageResponse<SecondaryRow> getSecondaryDiagnosisRows(
            LocalDate from, LocalDate to, Integer limit, Integer page, Integer size) {
        return ListPage.of(statisticsService.getDiagnosisStatistics(from, to, limit).secondaryDiagnoses(), page, size);
    }

    private static <T> List<T> first(List<T> rows, int count) {
        return List.copyOf(rows.subList(0, Math.min(count, rows.size())));
    }

    // dong co 1 trong cac truong chua tu khoa (bo dau); khong co tu khoa thi giu het
    private static <T> List<T> matching(List<T> rows, String search, List<Function<T, String>> fields) {
        String keyword = VietnameseText.fold(search);
        if (keyword == null || keyword.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> fields.stream().anyMatch(field -> {
                    String value = VietnameseText.fold(field.apply(row));
                    return value != null && value.contains(keyword);
                }))
                .toList();
    }
}
