package clinic.backend.dto.request;

import java.util.List;

import clinic.backend.enums.ClinicalExaminationNextStep;
import clinic.backend.enums.ClinicalExaminationSaveMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveClinicalExaminationRequest(
        @NotNull ClinicalExaminationSaveMode mode,
        ClinicalExaminationNextStep nextStep,
        @Size(min = 2, max = 1000) String reasonForVisit,
        @Size(max = 4000) String presentIllness,
        @Size(max = 4000) String generalFinding,
        @Valid VitalSignRequest vitalSigns,
        @Valid List<ExaminationFindingRequest> organFindings,
        @Size(max = 1000) String preliminaryDiagnosis,
        @Size(max = 10) String preliminaryDiagnosisIcd10Code,
        @Size(max = 4000) String treatmentCarePerformed,
        Long version) {
}
