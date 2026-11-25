package clinic.backend.dto.request;

import java.util.List;

/**
 * UC-56 conclusion form. Lengths and required fields are checked by the service so each failure maps to a field
 * (MSG02, MSG08); {@code version} is the version the Doctor loaded (optimistic locking).
 *
 * @param diagnosis            Chẩn đoán (required)
 * @param mainIcd10Code        Bệnh chính, ICD-10 (required)
 * @param secondaryIcd10Codes  Bệnh kèm theo, ICD-10, in order
 * @param treatmentPlan        Hướng điều trị (required: {@code conclusions.huongdieutri} is NOT NULL)
 * @param treatmentResult      Kết quả điều trị: optional free text, no length limit
 * @param advice               Lời dặn (required)
 * @param summary              Tóm tắt bệnh án
 * @param note                 Ghi chú
 * @param confirmDiscouragedMain the Doctor keeps a main code discouraged as main diagnosis (AF-01, MSG66)
 */
public record SaveConclusionRequest(
        String diagnosis,
        String mainIcd10Code,
        List<String> secondaryIcd10Codes,
        String treatmentPlan,
        String treatmentResult,
        String advice,
        String summary,
        String note,
        boolean confirmDiscouragedMain,
        Long version
) {
}
