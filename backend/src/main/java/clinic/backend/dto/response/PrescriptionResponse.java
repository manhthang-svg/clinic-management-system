package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-59: the prescription form of a visit with the saved prescription, if any ({@code prescriptionId} null until
 * the first save).
 *
 * @param conclusionSaved   UC-59 precondition: the conclusion of the visit has been saved
 * @param diagnosis         Chẩn đoán of the saved conclusion
 * @param diagnoses         the conclusion's main diagnosis first, then the secondary ones
 * @param allergies         recorded drug allergies of the patient (red labels, BR-23)
 * @param longTermAllowed   a diagnosis is in the long-term prescription list, so more than 30 days is allowed
 * @param sent              BR-47: the prescription was sent and cannot change here
 */
public record PrescriptionResponse(
        UUID visitId,
        UUID prescriptionId,
        Long version,
        String prescriptionCode,
        String slipNumber,
        boolean conclusionSaved,
        String diagnosis,
        List<Icd10OptionResponse> diagnoses,
        List<String> allergies,
        boolean longTermAllowed,
        List<Line> lines,
        String notes,
        String advice,
        boolean sent,
        String doctorName,
        Instant prescribedAt
) {

    public record Line(
            UUID medicineId,
            String code,
            String name,
            String activeIngredient,
            String strength,
            String route,
            String unit,
            BigDecimal quantity,
            String dosePerTime,
            short timesPerDay,
            short days,
            String usage,
            String note
    ) {
    }
}
