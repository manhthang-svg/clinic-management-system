package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-61: the follow-up form of a visit, with its appointment if one is saved.
 *
 * @param visitDate                the visit date; the follow-up date must be after it (BR-61-02)
 * @param minDate                  the earliest allowed follow-up date
 * @param maxDate                  the latest allowed follow-up date (365 days after the visit)
 * @param conclusionSaved          EF-03: the conclusion is saved, so an appointment can be made
 * @param diagnoses                the conclusion's main diagnosis first, then the secondary ones
 * @param appointment              the saved appointment, or null
 * @param prescriptionAccepted     AF-03: the prescription was accepted by the national prescription system
 * @param prescriptionFollowUpDate the follow-up date sent with that prescription, if any (BR-59-08)
 */
public record FollowUpScheduleResponse(
        UUID visitId,
        LocalDate visitDate,
        LocalDate minDate,
        LocalDate maxDate,
        boolean conclusionSaved,
        List<Icd10OptionResponse> diagnoses,
        Appointment appointment,
        boolean prescriptionAccepted,
        LocalDate prescriptionFollowUpDate
) {

    /** The saved appointment (BR-61-01). {@code status} is SCHEDULED, ARRIVED, MISSED or CANCELLED. */
    public record Appointment(
            UUID id,
            Long version,
            String slipNumber,
            LocalDate followUpDate,
            String advice,
            String status,
            Instant printedAt
    ) {
    }
}
