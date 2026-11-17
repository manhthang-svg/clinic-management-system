package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-28 summary for the page: totals and the chart rows only. The full doctor table comes page by page from
 * {@code /statistics/doctors/rows}, so the browser never receives the whole list.
 */
public record DoctorStatisticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        long examinedVisits,
        long visitsWithoutDoctor,
        long doctorCount,
        long workingDoctors,
        List<DoctorStatisticsResponse.DoctorRow> topDoctors) {
}
