package clinic.backend.dto.request;

/**
 * UC-90: send the prescription version the Doctor sees.
 *
 * @param version  the prescription version shown on the form (optimistic locking)
 * @param password the Doctor's password of the national prescription system (QĐ 808 VI), only when the server asked
 *                 for a sign-in (NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED); used once, never stored or logged
 */
public record SubmitPrescriptionRequest(Long version, char[] password) {

    @Override
    public String toString() {
        return "SubmitPrescriptionRequest[version=" + version + "]"; // never the password
    }
}
