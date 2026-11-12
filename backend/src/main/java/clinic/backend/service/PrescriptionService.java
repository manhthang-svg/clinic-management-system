package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-59 Prescribe Medication. */
public interface PrescriptionService {

    /** The prescription form of the visit, with any saved prescription. */
    PrescriptionResponse get(StaffPrincipal principal, UUID visitId);

    /** Saves the visit's single prescription; the medical record stays open. */
    PrescriptionResponse save(StaffPrincipal principal, UUID visitId, SavePrescriptionRequest request);

    /** Active ordinary medicines whose name, active ingredient or code contains the keyword. */
    List<PrescribableMedicineResponse> searchMedicines(String keyword, int limit);
}
