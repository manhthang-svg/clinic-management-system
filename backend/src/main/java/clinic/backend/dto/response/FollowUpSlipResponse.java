package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-62: the Phiếu hẹn khám lại of TT 01/2025/TT-BYT Phụ lục V (BR-61-06).
 *
 * @param printedAt  the time of this print; null in the preview
 * @param printedBy  who printed it; null in the preview
 * @param insurance  the insurance card of an insurance visit; null for a self-paid visit
 * @param visitDate  "Ngày khám bệnh"
 * @param diagnosis  "Chẩn đoán": the conclusion's main diagnosis
 * @param secondaryDiagnoses "Bệnh kèm theo"
 * @param doctorName the Doctor's academic title and name
 */
public record FollowUpSlipResponse(
        UUID appointmentId,
        String slipNumber,
        Instant printedAt,
        String printedBy,
        Facility facility,
        PatientInfo patient,
        Insurance insurance,
        LocalDate visitDate,
        Icd10OptionResponse diagnosis,
        List<Icd10OptionResponse> secondaryDiagnoses,
        LocalDate followUpDate,
        String advice,
        String doctorName
) {

    public record Facility(String name, String address, String phone) {
    }

    /** {@code gender} is "Nam", "Nữ" or null. */
    public record PatientInfo(String fullName, String gender, LocalDate birthDate, String address) {
    }

    /** "Số thẻ BHYT" and the card's validity ("Hạn sử dụng: Từ … Đến …"). */
    public record Insurance(String cardNumber, LocalDate validFrom, LocalDate validTo) {
    }
}
