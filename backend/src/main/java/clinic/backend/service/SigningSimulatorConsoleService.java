package clinic.backend.service;

import java.util.List;

import clinic.backend.dto.request.IssueSimulatedCertificateRequest;
import clinic.backend.dto.request.SigningSimulatorCredentialsRequest;
import clinic.backend.dto.response.SimulatedCertificateResponse;
import clinic.backend.dto.response.SimulatedSigningRequestResponse;

/**
 * Dev profile only: the simulated CA app the Doctor approves signatures in, and the simulated CA's certificate
 * administration (docs/uc-59-e-prescription.md, Known limitations). Not part of any use case.
 */
public interface SigningSimulatorConsoleService {

    List<SimulatedCertificateResponse> certificates();

    /** Issues a simulated certificate for a Doctor and registers it as the account's signing certificate. */
    SimulatedCertificateResponse issue(IssueSimulatedCertificateRequest request);

    /** Revokes a simulated certificate, to demonstrate UC-90 EF-11. */
    void revoke(String serial);

    List<SimulatedSigningRequestResponse> pendingRequests(SigningSimulatorCredentialsRequest credentials);

    void approve(String requestId, SigningSimulatorCredentialsRequest credentials);

    void decline(String requestId, SigningSimulatorCredentialsRequest credentials);
}
