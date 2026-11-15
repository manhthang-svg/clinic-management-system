package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.response.PrescriptionPrintResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-60 Print Prescription. */
public interface PrescriptionPrintService {

    /** UC-60 step 2: the printed form of an accepted prescription, for the preview; nothing is recorded. */
    PrescriptionPrintResponse printPreview(StaffPrincipal principal, UUID prescriptionId);

    /** UC-60 step 3: records the print ({@code PRINT} audit row, first {@code printed_at}) and returns the form. */
    PrescriptionPrintResponse preparePrint(StaffPrincipal principal, UUID prescriptionId);
}
