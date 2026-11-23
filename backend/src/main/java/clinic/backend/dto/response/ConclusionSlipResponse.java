package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-57: the conclusion slip of a closed record (Report 3 UC-57 step 2; docs/uc-57-print-conclusion.md).
 *
 * @param copy               the conclusion was printed before, so this print carries "Bản sao" (PO 23/11/2026)
 * @param printedAt          the time of this print; null in the preview
 * @param printedBy          who printed it; null in the preview
 * @param diagnosis          "Chẩn đoán": the main diagnosis with its ICD-10 code
 * @param secondaryDiagnoses "Bệnh kèm theo"
 * @param diagnosisText      the Doctor's own wording of the diagnosis (CV 365 III.13)
 * @param treatmentResult    "Kết quả điều trị" as its label (CV 365 III.31.1)
 * @param doctorName         the Doctor's academic title and name
 */
public record ConclusionSlipResponse(
        UUID visitId,
        String visitCode,
        boolean copy,
        Instant printedAt,
        String printedBy,
        Facility facility,
        PatientInfo patient,
        LocalDate visitDate,
        String roomName,
        Icd10OptionResponse diagnosis,
        List<Icd10OptionResponse> secondaryDiagnoses,
        String diagnosisText,
        String treatmentPlan,
        String treatmentResult,
        String advice,
        Instant concludedAt,
        String doctorName
) {

    /** Facility header (UC-77). */
    public record Facility(String name, String address, String phone) {
    }

    /** {@code gender} is "Nam", "Nữ" or null. */
    public record PatientInfo(String patientCode, String fullName, LocalDate birthDate, String gender, String address) {
    }
}
