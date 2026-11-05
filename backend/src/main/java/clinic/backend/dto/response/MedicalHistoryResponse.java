package clinic.backend.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.AllergySeverity;
import clinic.backend.enums.MedicalHistoryCategory;

/**
 * UC-39 step 2: the medical history and drug allergies of the patient of a visit.
 *
 * @param editable       false when the visit is not being examined in the Doctor's room or its record is closed
 *                       (EF-02); the panel is then read-only
 * @param readOnlyReason {@code NOT_IN_EXAMINATION} or {@code MEDICAL_RECORD_CLOSED} when not editable
 * @param updatedAt      time of the last change, null when nothing has been recorded
 */
public record MedicalHistoryResponse(
        UUID visitId,
        UUID patientId,
        boolean editable,
        String readOnlyReason,
        List<HistoryEntry> history,
        List<AllergyEntry> allergies,
        boolean noKnownDrugAllergy,
        Instant updatedAt,
        String updatedByName
) {

    public record HistoryEntry(MedicalHistoryCategory category, String content) {
    }

    public record AllergyEntry(String drug, String reaction, AllergySeverity severity) {
    }
}
