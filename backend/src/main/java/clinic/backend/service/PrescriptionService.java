package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionPrintResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-59 Prescribe Medication. */
public interface PrescriptionService {

    /** The prescription form of the visit, with any saved prescription. */
    PrescriptionResponse get(StaffPrincipal principal, UUID visitId);

    /** Saves the visit's single prescription; the medical record stays open. */
    PrescriptionResponse save(StaffPrincipal principal, UUID visitId, SavePrescriptionRequest request);

    /**
     * Active ordinary medicines whose name, active ingredient, strength, internal code or registration number
     * contains the keyword, ignoring accents; at most 20 ordered by name.
     */
    List<PrescribableMedicineResponse> searchMedicines(String keyword);

    /** UC-59 AF-03: removes a prescription that was never sent, with its lines. */
    void delete(StaffPrincipal principal, UUID prescriptionId);

    /** UC-91: every send of the prescription, newest first, each with its signature and signature status. */
    List<PrescriptionResponse.Submission> submissions(StaffPrincipal principal, UUID prescriptionId);

    /** UC-60 step 2: the printed form of an accepted prescription, for the preview; nothing is recorded. */
    PrescriptionPrintResponse printPreview(StaffPrincipal principal, UUID prescriptionId);

    /** UC-60 step 3: records the print ({@code PRINT} audit row, first {@code printed_at}) and returns the form. */
    PrescriptionPrintResponse preparePrint(StaffPrincipal principal, UUID prescriptionId);
}
