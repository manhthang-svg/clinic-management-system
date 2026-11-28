package clinic.backend.service;

import java.util.List;

/**
 * The national prescription system as QĐ 808/QĐ-BYT publishes it (BR-45, BR-59-11, BR-59-17): sign-in of the
 * facility (III) and of the Doctor (VI), adding and removing a Doctor (IV, V) and sending a prescription (VII). Each
 * call is one request; keeping the tokens is the job of {@code NationalPrescriptionSessions}.
 *
 * <p>Every method throws {@code AppException(NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE)} when the system does not
 * answer in time. A password is passed as a {@code char[]} that the caller clears afterwards; no implementation logs,
 * stores or audits it (docs/security.md).
 */
public interface NationalPrescriptionSystem {

    /** III: the facility signs in with its link code and password. */
    SignIn signInFacility(String facilityLinkCode, char[] password);

    /** IV: adds the Doctor to the facility, with a token of {@link #signInFacility}. */
    Answer addPrescriber(String facilityToken, String prescriberLinkCode);

    /** V: removes the Doctor from the facility, with a token of {@link #signInFacility}. */
    Answer removePrescriber(String facilityToken, String prescriberLinkCode);

    /** VI: the Doctor signs in with their link code, the facility's link code and the Doctor's password. */
    SignIn signInPrescriber(String prescriberLinkCode, String facilityLinkCode, char[] password);

    /** VII: sends one prescription (QĐ 808 VII.3 JSON, UTF-8), with a token of {@link #signInPrescriber}. */
    Answer submit(String prescriberToken, byte[] prescriptionJson);

    /**
     * The answer of a sign-in (III.4–5, VI.4–5).
     *
     * @param token  the bearer token, null when refused
     * @param errors {@code danh_sach_cac_loi} of a refusal, empty when signed in
     */
    record SignIn(String token, List<String> errors) {

        public SignIn {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean succeeded() {
            return token != null;
        }

        @Override
        public String toString() {
            return "SignIn[succeeded=" + succeeded() + ", errors=" + errors + "]"; // never the token
        }
    }

    /**
     * @param accepted     HTTP 200 (IV.5, V.5, VII.5); otherwise HTTP 422 with {@code danh_sach_cac_loi}
     * @param tokenInvalid the token was refused as unknown or expired (ASSUMPTION: QĐ 808 names no such answer)
     * @param errors       the error list of a refusal, empty when accepted
     */
    record Answer(boolean accepted, boolean tokenInvalid, String message, List<String> errors) {

        public Answer {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public static Answer accepted(String message) {
            return new Answer(true, false, message, List.of());
        }

        public static Answer rejected(String message, List<String> errors) {
            return new Answer(false, false, message, errors);
        }

        public static Answer invalidToken() {
            return new Answer(false, true, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn",
                    List.of("Phiên đăng nhập không hợp lệ hoặc đã hết hạn."));
        }
    }
}
