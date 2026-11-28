package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-60: the printed form of an accepted prescription (TT 26/2025 Phụ lục I, BR-59-13). The diagnoses and the
 * follow-up date are the ones sent with the prescription (BR-59-08).
 *
 * @param copy      the prescription was printed before, so this print carries "Bản sao" (PO 23/11/2026)
 * @param printedAt the time of this print; null in the preview
 * @param printedBy who printed it (Luật GDĐT Điều 12 khoản 2); null in the preview
 */
public record PrescriptionPrintResponse(
        UUID prescriptionId,
        String prescriptionCode,
        boolean copy,
        Instant printedAt,
        String printedBy,
        Facility facility,
        PatientInfo patient,
        PrescriptionResponse.Child child,
        List<Icd10OptionResponse> diagnoses,
        List<Line> lines,
        String notes,
        String advice,
        LocalDate followUpDate,
        Instant prescribedAt,
        String doctorName
) {

    /** Facility header (UC-77). */
    public record Facility(String name, String address, String phone) {
    }

    /**
     * BR-59-07: administrative data read from the patient and visit.
     *
     * @param citizenId       ID number, if any (TT 26 "nếu có")
     * @param insuranceNumber the insurance number of an insurance visit only
     */
    public record PatientInfo(
            String fullName,
            LocalDate birthDate,
            String gender,
            String address,
            String phone,
            String citizenId,
            String passportNumber,
            String insuranceNumber
    ) {
    }

    /** One medicine; printed as "{activeIngredient} ({name}) {strength}" (BR-59-13). */
    public record Line(
            String activeIngredient,
            String name,
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
