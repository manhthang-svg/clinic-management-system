package clinic.backend.service;

import java.util.List;

/**
 * The national prescription system (QĐ 808/QĐ-BYT III, VI, VII; BR-59-11, BR-59-12). The facility and the Doctor
 * sign in with their link codes; the implementation obtains and reuses the 7-day token. Throws
 * {@code AppException(NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE)} when the system does not answer in time.
 */
public interface NationalPrescriptionSystem {

    /** Sends one signed prescription (QĐ 808 VII.3 JSON, UTF-8). */
    Answer submit(Credentials credentials, byte[] prescriptionJson);

    record Credentials(String facilityCode, String facilityLinkCode, String prescriberLinkCode) {
    }

    /**
     * @param accepted HTTP 200 (VII.5); otherwise HTTP 422 with {@code danh_sach_cac_loi} (VII.4)
     * @param errors   the error list of a rejection, empty when accepted
     */
    record Answer(boolean accepted, String message, List<String> errors) {

        public Answer {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
