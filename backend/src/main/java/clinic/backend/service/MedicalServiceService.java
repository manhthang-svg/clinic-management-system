package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.CreateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServicePriceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceStatusRequest;
import clinic.backend.dto.response.CreatedMedicalServiceResponse;
import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceFormOptionsResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.MedicalServiceStatusCheckResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.security.user.StaffPrincipal;

public interface MedicalServiceService {

    MedicalServiceDetailResponse getService(UUID serviceId);

    MedicalServiceFormOptionsResponse getFormOptions();

    CreatedMedicalServiceResponse createService(StaffPrincipal principal, CreateMedicalServiceRequest request);

    MedicalServiceDetailResponse updateService(
            StaffPrincipal principal, UUID serviceId, UpdateMedicalServiceRequest request);

    MedicalServiceDetailResponse updatePrices(UUID serviceId, UpdateMedicalServicePriceRequest request);

    MedicalServiceStatusCheckResponse checkDeactivation(UUID serviceId);

    MedicalServiceStatusCheckResponse checkActivation(UUID serviceId);

    MedicalServiceDetailResponse updateStatus(UUID serviceId, UpdateMedicalServiceStatusRequest request);

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
