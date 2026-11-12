package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-28 View Doctor Statistics. A visit counts for the Doctor who examined it (the Doctor who started the
 * examination; after a room transfer, the Doctor of the new room). Visits are counted by queue date. Visits no Doctor
 * has started yet (waiting, cancelled before examination) are counted in {@code visitsWithoutDoctor}.
 */
public record DoctorStatisticsResponse(
        LocalDate from,
        LocalDate to,
        long examinedVisits,
        long visitsWithoutDoctor,
        List<DoctorRow> doctors) {

    /**
     * One Doctor; every Active Doctor is listed, with zeros when they examined nobody in the period.
     * {@code completed + inProgress + abandoned + cancelled = visits}.
     */
    public record DoctorRow(
            UUID doctorId,
            String doctorName,
            boolean active,
            long visits,
            long patients,
            long completed,
            long inProgress,
            long abandoned,
            long cancelled) {
    }
}
