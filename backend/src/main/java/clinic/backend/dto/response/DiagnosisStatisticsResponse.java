package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-32 View Diagnosis Statistics: the most common diagnoses (ICD-10, BR-31) of the conclusions saved in the period
 * (concluded time, clinic time zone). A conclusion without an ICD-10 code is counted in {@code withoutIcd} only.
 */
public record DiagnosisStatisticsResponse(
        LocalDate from,
        LocalDate to,
        int limit,
        long conclusions,
        long withIcd,
        long withoutIcd,
        long distinctCodes,
        List<DiagnosisRow> mainDiagnoses,
        List<CategoryRow> categories,
        List<SecondaryRow> secondaryDiagnoses) {

    /** One ICD-10 code as main diagnosis: conclusions with it and distinct patients. */
    public record DiagnosisRow(String icdCode, String icdName, long cases, long patients) {
    }

    /** One ICD-10 category: the first three characters of the code (J06.9 → J06), named from the catalog. */
    public record CategoryRow(String categoryCode, String categoryName, long cases) {
    }

    /** One ICD-10 code as a secondary (comorbid) diagnosis. */
    public record SecondaryRow(String icdCode, String icdName, long cases) {
    }
}
