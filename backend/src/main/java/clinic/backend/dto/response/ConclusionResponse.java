package clinic.backend.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-56: the conclusion form of a visit, with the preliminary diagnosis of UC-47 for reference. The conclusion fields
 * are null until it is saved the first time ({@code conclusionId} null).
 *
 * @param diagnosesLocked BR-47: the diagnoses were sent with a prescription, so the main and secondary codes are
 *                        read-only
 */
public record ConclusionResponse(
        UUID visitId,
        UUID medicalRecordId,
        UUID conclusionId,
        Long version,
        String preliminaryDiagnosis,
        List<Icd10OptionResponse> preliminaryDiagnoses,
        String diagnosis,
        Icd10OptionResponse mainDiagnosis,
        List<Icd10OptionResponse> secondaryDiagnoses,
        String treatmentPlan,
        Short treatmentResult,
        String advice,
        String summary,
        String note,
        boolean diagnosesLocked,
        String doctorName,
        Instant concludedAt
) {
}
