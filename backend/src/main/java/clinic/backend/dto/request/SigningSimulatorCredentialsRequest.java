package clinic.backend.dto.request;

/**
 * Sign-in to the simulated CA app (dev profile only): the certificate serial and the PIN the simulated CA keeps.
 * Sent in a body, never in a URL.
 */
public record SigningSimulatorCredentialsRequest(String serial, String pin) {
}
