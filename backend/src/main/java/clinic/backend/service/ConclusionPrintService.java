package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.response.ConclusionSlipResponse;
import clinic.backend.security.user.StaffPrincipal;

/** BF-04 "In phiếu kết luận": the saved conclusion of the Doctor's visit, ready to print. */
public interface ConclusionPrintService {

    /** The slip of the saved conclusion; nothing is changed and the print is not logged. */
    ConclusionSlipResponse print(StaffPrincipal principal, UUID visitId);
}
