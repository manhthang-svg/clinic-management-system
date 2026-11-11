package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        Integer queueNumber,
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
        boolean reprint,
        LocalDate dateOfBirth,
        String phone,
        /** Street, ward and province joined; null when none is recorded. */
        String address,
        String occupation,
        String ethnicity,
        /** Insurance card of the visit; null for a self-paid visit. */
        String insuranceCardNumber,
        LocalDate insuranceValidFrom,
        LocalDate insuranceValidTo,
        String reasonForVisit,
        /** Full name of the Receptionist who registered the visit. */
        String registeredByName,
        /** Address of the clinic facility, printed under its name. */
        String facilityAddress
) {
}
