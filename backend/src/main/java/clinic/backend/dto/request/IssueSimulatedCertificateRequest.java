package clinic.backend.dto.request;

/**
 * Dev profile only: issue a simulated signing certificate for a Doctor's account and register it (BR-59-16, until
 * the Admin use case exists).
 *
 * @param username the Doctor's user name
 * @param pin      the PIN the Doctor will use in the simulated CA app
 */
public record IssueSimulatedCertificateRequest(String username, String pin) {
}
