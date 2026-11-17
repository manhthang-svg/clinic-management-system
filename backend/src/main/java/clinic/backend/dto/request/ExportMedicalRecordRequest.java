package clinic.backend.dto.request;

/**
 * UC-53 export of a closed medical record as a PDF in form 15/BV1.
 *
 * @param purpose   "Mục đích xuất" (BR-43, BR-131): LIEN_THONG, LUU_TRU, CUNG_CAP_NGUOI_BENH or
 *                  CO_QUAN_CO_THAM_QUYEN; required
 * @param recipient "Người nhận" (BR-53-03): required for CUNG_CAP_NGUOI_BENH and CO_QUAN_CO_THAM_QUYEN, at most
 *                  200 characters
 */
public record ExportMedicalRecordRequest(String purpose, String recipient) {
}
