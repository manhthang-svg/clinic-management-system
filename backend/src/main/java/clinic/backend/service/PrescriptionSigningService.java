package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.RequestSignatureRequest;
import clinic.backend.dto.response.SigningRequestResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-90 Submit e-Prescription: sign with the Doctor's digital signature, then send (BR-59-10, BR-59-11). */
public interface PrescriptionSigningService {

    /** UC-90 step 3: re-checks the prescription, pre-checks the certificate and asks the CA for a signature. */
    SigningRequestResponse requestSignature(StaffPrincipal principal, UUID prescriptionId, RequestSignatureRequest request);

    /** UC-90 AF-06: the pending request of the prescription, or null. */
    SigningRequestResponse currentRequest(StaffPrincipal principal, UUID prescriptionId);

    /**
     * UC-90 steps 5-8: while the CA has no answer returns PENDING; once signed, verifies, stores and sends.
     * Idempotent on a final request.
     */
    SigningRequestResponse complete(StaffPrincipal principal, UUID prescriptionId, UUID requestId);

    /** UC-90 AF-03: cancels a pending request; no effect on a final one. */
    void cancel(StaffPrincipal principal, UUID prescriptionId, UUID requestId);
}
