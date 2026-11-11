package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** UC-26 View Statistics Dashboard: organisation-wide overview for the selected period. */
public record DashboardStatisticsResponse(
        LocalDate from,
        LocalDate to,
        VisitSummary visits,
        long distinctPatients,
        long paraclinicalServicesCompleted,
        RevenueSummary revenue,
        List<DailyVisits> dailyVisits,
        List<RoomVisits> topRooms,
        List<DiagnosisCount> topDiagnoses) {

    /** Visits registered in the period, by outcome and by patient type. */
    public record VisitSummary(
            long total,
            long completed,
            long inProgress,
            long cancelled,
            long abandoned,
            long insurance,
            long selfPay) {
    }

    /** Payments confirmed in the period; insurance and patient shares are separate (BR-38). */
    public record RevenueSummary(
            long paymentCount,
            BigDecimal totalAmount,
            BigDecimal insuranceAmount,
            BigDecimal patientAmount) {
    }

    /** One entry per day of the period, zero when no visit was registered. */
    public record DailyVisits(LocalDate date, long total) {
    }

    public record RoomVisits(UUID roomId, String roomName, long total) {
    }

    public record DiagnosisCount(String icdCode, String icdName, long total) {
    }
}
