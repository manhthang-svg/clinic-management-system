package clinic.backend.dto.request;

import java.time.LocalDate;

/**
 * UC-61 step 3: the follow-up appointment of the visit (BR-61-02).
 *
 * @param followUpDate the follow-up date; required
 * @param advice       "Lời dặn tái khám"; optional, at most 2,000 characters
 * @param version      the loaded version of an existing appointment; null for the first save
 */
public record SaveFollowUpAppointmentRequest(LocalDate followUpDate, String advice, Long version) {
}
