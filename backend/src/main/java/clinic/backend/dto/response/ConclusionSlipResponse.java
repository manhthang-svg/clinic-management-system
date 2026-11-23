package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import clinic.backend.enums.VisitDoituongbnLoai;

/**
 * The printed examination conclusion of a visit (BF-04, "In phiếu kết luận"). **ASSUMPTION:** no form of TT 32/2023
 * is required for an outpatient conclusion; the slip shows the conclusion as the Doctor saved it.
 *
 * @param printedAt          the time of this print, shown on the edge of the sheet
 * @param insuranceCardNumber the card of an insurance visit; null for a self-paid visit
 * @param mainDiagnosis      "Bệnh chính" (ICD-10)
 * @param secondaryDiagnoses "Bệnh kèm theo" (ICD-10)
 * @param treatmentResult    1 Khỏi, 2 Đỡ, … as in the conclusion form
 * @param doctorName         the concluding Doctor's academic title and name
 */
public record ConclusionSlipResponse(
        Instant printedAt,
        Facility facility,
        PatientInfo patient,
        String visitCode,
        VisitDoituongbnLoai visitType,
        String insuranceCardNumber,
        Instant registeredAt,
        String diagnosis,
        Icd10OptionResponse mainDiagnosis,
        List<Icd10OptionResponse> secondaryDiagnoses,
        String treatmentPlan,
        Short treatmentResult,
        String advice,
        String doctorName,
        Instant concludedAt
) {
    public record Facility(String name, String address, String phone) {
    }

    /** {@code gender} is "Nam", "Nữ" or "Khác". */
    public record PatientInfo(
            String fullName, String patientCode, String gender, LocalDate birthDate, String address) {
    }
}
