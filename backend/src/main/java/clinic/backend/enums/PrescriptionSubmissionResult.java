package clinic.backend.enums;

/**
 * Outcome of one attempt to send a prescription to the national prescription system (BR-47).
 */
public enum PrescriptionSubmissionResult {
    ACCEPTED,
    REJECTED,
    /** The system did not answer (MSG86). */
    NO_RESPONSE
}
