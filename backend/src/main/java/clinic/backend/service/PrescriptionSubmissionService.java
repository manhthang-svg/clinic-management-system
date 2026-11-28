package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.SubmitPrescriptionRequest;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-90 Submit e-Prescription (and the resend of UC-91 AF-01 and UC-92). */
public interface PrescriptionSubmissionService {

    /**
     * Checks the prescription, signs the Doctor in to the national prescription system when no token is kept, and
     * sends it. Returns the prescription after an accepted or rejected answer.
     */
    PrescriptionResponse submit(StaffPrincipal principal, UUID prescriptionId, SubmitPrescriptionRequest request);
}
