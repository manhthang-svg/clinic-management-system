package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;

public interface MedicalServiceService {

    PageResponse<MedicalServiceSummaryResponse> listServices(
            String keyword,
            MedicalServiceType type,
            UUID serviceGroupId,
            UUID departmentId,
            ActivationStatus status,
            int page,
            int size,
            String sortField,
            boolean descending);
}
