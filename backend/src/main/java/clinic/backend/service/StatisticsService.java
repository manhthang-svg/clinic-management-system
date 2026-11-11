package clinic.backend.service;

import java.time.LocalDate;

import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.enums.StatisticsGroupBy;

/** Admin statistics: UC-26 View Statistics Dashboard, UC-27 View Patient Visit Statistics. */
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
}
