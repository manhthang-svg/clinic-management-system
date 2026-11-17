package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-32 summary for the page: totals and the most common main diagnoses for the chart. The three tables come page
 * by page from {@code /statistics/diagnoses/main}, {@code /categories} and {@code /secondary}.
 */
public record DiagnosisStatisticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        int limit,
        long conclusions,
        long withIcd,
        long withoutIcd,
        long distinctCodes,
        List<DiagnosisStatisticsResponse.DiagnosisRow> topDiagnoses) {
}
