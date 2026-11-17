package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.ConfirmConclusionRequest;
import clinic.backend.dto.response.ConclusionClosureResponse;
import clinic.backend.dto.response.ConclusionConfirmationResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-58 Close Medical Record (docs/uc-58-close-medical-record.md). */
public interface MedicalRecordClosureService {

    /** What "Xác nhận kết luận" needs (BR-58-01); records nothing. */
    ConclusionClosureResponse closureCheck(StaffPrincipal principal, UUID visitId);

    /**
     * Confirms the conclusion and, in the same transaction, completes the visit, fixes the insurance charges, locks
     * and archives the record and records the Doctor's electronic confirmation (BR-58-01 to BR-58-09).
     */
    ConclusionConfirmationResponse confirm(StaffPrincipal principal, UUID visitId, ConfirmConclusionRequest request);
}
