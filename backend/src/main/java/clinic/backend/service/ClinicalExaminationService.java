package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.MedicalHistoryItemRequest;
import clinic.backend.dto.request.SaveClinicalExaminationRequest;
import clinic.backend.dto.response.ClinicalExaminationResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.MedicalHistoryItemResponse;
import clinic.backend.security.user.StaffPrincipal;

public interface ClinicalExaminationService {
    ClinicalExaminationResponse open(StaffPrincipal principal, UUID visitId);

    ClinicalExaminationResponse save(
            StaffPrincipal principal,
            UUID visitId,
            SaveClinicalExaminationRequest request);

    List<Icd10OptionResponse> searchActiveIcd10(String keyword, int limit);

    /** UC-39 within UC-47 (AF-02): returns the patient's refreshed history and allergies. */
    List<MedicalHistoryItemResponse> addMedicalHistory(
            StaffPrincipal principal, UUID visitId, MedicalHistoryItemRequest request);

    List<MedicalHistoryItemResponse> updateMedicalHistory(
            StaffPrincipal principal, UUID visitId, UUID historyId, MedicalHistoryItemRequest request);
}
