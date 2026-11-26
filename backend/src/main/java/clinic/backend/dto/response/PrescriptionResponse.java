package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * @param prescriberLinkCodeMissing EF-07: the Doctor has no prescriber link code, so the form is disabled
 * @param facilityLinkCodeMissing   EF-07: the facility has no facility code or prescription link code
 * @param followUpDate      the scheduled follow-up date of the visit, if any (BR-59-08)
 * @param child             BR-59-07: present only for a patient under 72 months on the prescribing date
 * @param status            BR-59-09: PENDING, SENT, ACCEPTED or REJECTED; null before the first save
 * @param signingCertificateRegistered UC-90 EF-10: the Doctor has a registered signing certificate
 * @param lastSubmission    UC-91: the latest send and its answer, if any
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
        Instant prescribedAt,
        boolean prescriberLinkCodeMissing,
        boolean facilityLinkCodeMissing,
        LocalDate followUpDate,
        Child child,
        String status,
        boolean signingCertificateRegistered,
        Submission lastSubmission
) {

    /**
     * One send to the national prescription system (UC-91).
     *
     * @param result      ACCEPTED, REJECTED or NO_RESPONSE
     * @param errors      the error list of a rejection (MSG134)
     * @param respondedAt null when the system did not answer
     * @param signedAt    time-stamp time of the digital signature of this send
     * @param signerName  the signing Doctor
     * @param certificateSerial serial of the signing certificate
     * @param certificateIssuer issuer of the signing certificate
     * @param submittedBy       the Doctor who sent it
     * @param signatureValid    BR-59-16: the stored signature verified again now; null when the send has none
     * @param signatureFailureReason why the signature is not valid (MSG124 reasons); null when valid
     */
    public record Submission(
            short attemptNo,
            String result,
            List<String> errors,
            Instant submittedAt,
            Instant respondedAt,
            Instant signedAt,
            String signerName,
            String certificateSerial,
            String certificateIssuer,
            String submittedBy,
            Boolean signatureValid,
            String signatureFailureReason) {
    }

    /**
     * Data a prescription for a child under 72 months must carry (TT 26/2025 Điều 6 khoản 4); a null value is
     * missing and must be recorded before sending.
     *
     * @param ageMonths          age in months on the prescribing date
     * @param weightKg           latest weight recorded in this visit's vital signs
     * @param accompanyingPerson the patient's relative (hotennguoithan)
     */
    public record Child(int ageMonths, BigDecimal weightKg, String accompanyingPerson) {
    }

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
