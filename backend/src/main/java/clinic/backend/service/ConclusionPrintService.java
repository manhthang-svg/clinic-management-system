package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.response.ConclusionSlipResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-57 Print Conclusion, by the Doctor of the visit once its record is closed. */
public interface ConclusionPrintService {

    /** UC-57 step 2: the conclusion slip for the preview; nothing is recorded. */
    ConclusionSlipResponse printPreview(StaffPrincipal principal, UUID visitId);

    /** UC-57 step 3: returns the slip with the print time; nothing is recorded. */
    ConclusionSlipResponse preparePrint(StaffPrincipal principal, UUID visitId);
}
