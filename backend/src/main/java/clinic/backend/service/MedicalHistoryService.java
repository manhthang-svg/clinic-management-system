package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.UpdateMedicalHistoryRequest;
import clinic.backend.dto.response.MedicalHistoryResponse;

/**
 * UC-39 Update Medical History and Allergies (BR-23).
 */
public interface MedicalHistoryService {

    MedicalHistoryResponse getForVisit(UUID doctorAccountId, UUID visitId);

    MedicalHistoryResponse updateForVisit(UUID doctorAccountId, UUID visitId, UpdateMedicalHistoryRequest request);
}
