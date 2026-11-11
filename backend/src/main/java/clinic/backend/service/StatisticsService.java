package clinic.backend.service;

import java.time.LocalDate;

import clinic.backend.dto.response.DashboardStatisticsResponse;

/** UC-26 View Statistics Dashboard. */
public interface StatisticsService {

    /**
     * Overview for {@code [from, to]}, both inclusive. A missing {@code from} means the first day of
     * the current month; a missing {@code to} means today.
     */
    DashboardStatisticsResponse getDashboard(LocalDate from, LocalDate to);
}
