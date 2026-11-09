package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.CreateMedicineRequest;
import clinic.backend.dto.request.UpdateMedicineRequest;
import clinic.backend.dto.request.UpdateMedicineStatusRequest;
import clinic.backend.dto.response.CreatedMedicineResponse;
import clinic.backend.dto.response.MedicineDetailResponse;
import clinic.backend.dto.response.MedicineFormOptionsResponse;
import clinic.backend.dto.response.MedicineSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.security.user.StaffPrincipal;

/** Medicine catalog managed by the Admin (UC-81 to UC-84, I1-D46). */
public interface MedicineCatalogService {

    /** UC-81 list: the keyword matches name, active ingredient, strength, code and registration number. */
    PageResponse<MedicineSummaryResponse> listMedicines(
            String keyword,
            String unit,
            String route,
            MedicineControlGroup controlGroup,
            ActivationStatus status,
            int page,
            int size,
            String sortField,
            boolean descending);

    /** UC-81 detail. */
    MedicineDetailResponse getMedicine(UUID medicineId);

    MedicineFormOptionsResponse getFormOptions();

    /** UC-82: the new medicine is active. */
    CreatedMedicineResponse createMedicine(StaffPrincipal principal, CreateMedicineRequest request);

    /** UC-83: every field except the code. */
    MedicineDetailResponse updateMedicine(StaffPrincipal principal, UUID medicineId, UpdateMedicineRequest request);

    /** UC-84. */
    MedicineDetailResponse updateStatus(StaffPrincipal principal, UUID medicineId, UpdateMedicineStatusRequest request);
}
