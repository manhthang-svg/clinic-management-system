package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

/** Canonical UC-41 examination-ticket data assembled from persisted registration data. */
public record VisitTicketResponse(
        UUID visitId,
        String facilityName,
        String registrationCode,
        String patientCode,
        String patientName,
        Integer birthYear,
        String gender,
        int queueNumber,
        String roomName,
        String roomLocation,
        List<String> doctorNames,
        VisitDoituongbnLoai visitType,
        Short coverageRate,
        boolean priority,
        String priorityReason,
        Instant registeredAt,
        BigDecimal patientAmount,
        VisitStatus visitStatus,
        boolean reprint
) {
}
