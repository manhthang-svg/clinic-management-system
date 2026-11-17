package clinic.backend.service;

import java.time.LocalDate;

import clinic.backend.dto.response.DepartmentStatisticsOverviewResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse.DepartmentRow;
import clinic.backend.dto.response.DiagnosisStatisticsOverviewResponse;
import clinic.backend.dto.response.DiagnosisStatisticsResponse.CategoryRow;
import clinic.backend.dto.response.DiagnosisStatisticsResponse.DiagnosisRow;
import clinic.backend.dto.response.DiagnosisStatisticsResponse.SecondaryRow;
import clinic.backend.dto.response.DoctorStatisticsOverviewResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse.DoctorRow;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsOverviewResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse.ServiceCount;
import clinic.backend.dto.response.RevenueStatisticsResponse.PeriodAmounts;
import clinic.backend.dto.response.VisitStatisticsResponse.VisitPeriod;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.StatisticsGroupBy;

/**
 * Statistics pages (UC-27 to UC-32) as the browser shows them: a summary for the cards and charts, and the tables
 * filtered and cut into pages on the server, so the browser only receives the rows on screen. Page numbers start at 0;
 * the size defaults to 20.
 */
public interface StatisticsListService {

    PageResponse<VisitPeriod> getVisitRows(
            LocalDate from, LocalDate to, StatisticsGroupBy groupBy, Integer page, Integer size);

    PageResponse<PeriodAmounts> getRevenueRows(
            LocalDate from, LocalDate to, StatisticsGroupBy groupBy, Integer page, Integer size);

    DoctorStatisticsOverviewResponse getDoctorOverview(LocalDate from, LocalDate to);

    PageResponse<DoctorRow> getDoctorRows(LocalDate from, LocalDate to, String search, Integer page, Integer size);

    DepartmentStatisticsOverviewResponse getDepartmentOverview(LocalDate from, LocalDate to);

    PageResponse<DepartmentRow> getDepartmentRows(
            LocalDate from, LocalDate to, DepartmentType type, Integer page, Integer size);

    ParaclinicalStatisticsOverviewResponse getParaclinicalOverview(LocalDate from, LocalDate to);

    PageResponse<ServiceCount> getParaclinicalServiceRows(
            LocalDate from, LocalDate to, String search, Integer page, Integer size);

    DiagnosisStatisticsOverviewResponse getDiagnosisOverview(LocalDate from, LocalDate to, Integer limit);

    PageResponse<DiagnosisRow> getMainDiagnosisRows(
            LocalDate from, LocalDate to, Integer limit, Integer page, Integer size);

    PageResponse<CategoryRow> getDiagnosisCategoryRows(
            LocalDate from, LocalDate to, Integer limit, Integer page, Integer size);

    PageResponse<SecondaryRow> getSecondaryDiagnosisRows(
            LocalDate from, LocalDate to, Integer limit, Integer page, Integer size);
}
