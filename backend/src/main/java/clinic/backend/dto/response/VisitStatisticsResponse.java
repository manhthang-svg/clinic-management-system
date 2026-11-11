package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

import clinic.backend.enums.StatisticsGroupBy;

/**
 * UC-27 View Patient Visit Statistics. Visits are counted by queue date. {@code series} has one entry
 * per day or per month of the period, zero-filled; a month entry is keyed by its first day even when
 * the period starts later in that month.
 */
public record VisitStatisticsResponse(
        LocalDate from,
        LocalDate to,
        StatisticsGroupBy groupBy,
        VisitCounts summary,
        List<VisitPeriod> series) {

    /**
     * Visit counts. {@code completed + inProgress + cancelled + abandoned = total} and
     * {@code insurance + selfPay = total}; {@code followUp} and {@code priority} are subsets of total.
     */
    public record VisitCounts(
            long total,
            long completed,
            long inProgress,
            long cancelled,
            long abandoned,
            long insurance,
            long selfPay,
            long followUp,
            long priority) {
    }

    public record VisitPeriod(LocalDate period, VisitCounts counts) {
    }
}
