package clinic.backend.dto.request;

/**
 * UC-93: add a Doctor to, or remove one from, the facility on the national prescription system.
 *
 * @param version          the account version the Admin sees (optimistic locking)
 * @param facilityPassword the facility's password of the national prescription system (QĐ 808 III), only when the
 *                         server asked for a sign-in (NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED); used once, never
 *                         stored or logged
 */
public record NationalPrescriberRequest(Long version, char[] facilityPassword) {

    @Override
    public String toString() {
        return "NationalPrescriberRequest[version=" + version + "]"; // never the password
    }
}
