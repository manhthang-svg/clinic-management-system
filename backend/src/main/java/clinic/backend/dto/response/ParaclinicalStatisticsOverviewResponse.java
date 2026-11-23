package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-30 summary for the page: totals, the counts by type and group, and the busiest rooms for the chart. The service
 * table comes page by page from {@code /statistics/paraclinical/services}.
 */
public record ParaclinicalStatisticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        long performed,
        long withResult,
        List<ParaclinicalStatisticsResponse.TypeCount> byType,
        List<ParaclinicalStatisticsResponse.GroupCount> byGroup,
        long serviceCount,
        long roomCount,
        List<ParaclinicalStatisticsResponse.RoomCount> topRooms) {
}
