package clinic.backend.dto.request;

import clinic.backend.enums.ClinicalExaminationNextStep;
import clinic.backend.enums.ClinicalExaminationSaveMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveClinicalExaminationRequest(
        @NotNull ClinicalExaminationSaveMode mode,
        ClinicalExaminationNextStep nextStep,
        @Size(min = 2, max = 1000) String reasonForVisit,
        /** BR-129: required when the visit was referred by a health facility; ignored otherwise. */
        @Size(max = 1000) String referralDiagnosis,
        @Size(max = 4000) String presentIllness,
        @Size(max = 4000) String generalFinding,
        VitalSignRequest vitalSigns,
        @Size(max = 2000) String organFinding,
        @Size(max = 1000) String preliminaryDiagnosis,
        @Size(max = 10) String preliminaryDiagnosisIcd10Code,
        @Size(max = 20) List<@Size(max = 10) String> preliminaryDiagnosisIcd10Codes,
        @Size(max = 4000) String treatmentCarePerformed,
        Long version) {
}
