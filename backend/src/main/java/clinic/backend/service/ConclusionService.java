package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.SaveConclusionRequest;
import clinic.backend.dto.response.ConclusionResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-56 Conclude Examination. */
public interface ConclusionService {

    /** The conclusion form of the visit, with any saved data. */
    ConclusionResponse get(StaffPrincipal principal, UUID visitId);

    /** Saves the conclusion; the medical record stays open (prescription, follow-up, closing). */
    ConclusionResponse save(StaffPrincipal principal, UUID visitId, SaveConclusionRequest request);
}
