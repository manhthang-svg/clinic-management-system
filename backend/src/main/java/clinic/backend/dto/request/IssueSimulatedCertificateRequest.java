package clinic.backend.dto.request;

/**
 * Dev profile only: issue a simulated signing certificate to a Doctor and register it on the Doctor's account
 * (BR-59-16, until the Admin use case exists). Like a real CA, the simulator knows the subscriber by the personal
 * identification number, not by the clinic user name.
 *
 * @param citizenId the Doctor's 12-digit personal identification number, as recorded on the account
 * @param pin       the PIN the Doctor will use in the simulated CA app
 */
public record IssueSimulatedCertificateRequest(String citizenId, String pin) {
}
