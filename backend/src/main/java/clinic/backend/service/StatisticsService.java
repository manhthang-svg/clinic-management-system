package clinic.backend.service;

import java.time.LocalDate;

import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.enums.StatisticsGroupBy;

/** Admin statistics: UC-26 dashboard, UC-27 visit statistics, UC-28 doctor statistics, UC-29 department and room statistics, UC-30 paraclinical service statistics. */
public interface StatisticsService {

    /**
     * Overview for {@code [from, to]}, both inclusive. A missing {@code from} means the first day of
     * the current month; a missing {@code to} means today.
     */
    DashboardStatisticsResponse getDashboard(LocalDate from, LocalDate to);

    /**
     * UC-27 View Patient Visit Statistics for {@code [from, to]}, bucketed by day or month (default day).
     * Missing dates default as in {@link #getDashboard}.
     */
    VisitStatisticsResponse getVisitStatistics(LocalDate from, LocalDate to, StatisticsGroupBy groupBy);

    /** UC-28 View Doctor Statistics for {@code [from, to]}; missing dates default as in {@link #getDashboard}. */
    DoctorStatisticsResponse getDoctorStatistics(LocalDate from, LocalDate to);

    /** UC-29 View Department and Room Statistics for {@code [from, to]}; missing dates default as in {@link #getDashboard}. */
    DepartmentStatisticsResponse getDepartmentStatistics(LocalDate from, LocalDate to);

    /** UC-30 View Paraclinical Service Statistics for {@code [from, to]}; missing dates default as in {@link #getDashboard}. */
    ParaclinicalStatisticsResponse getParaclinicalStatistics(LocalDate from, LocalDate to);
}
