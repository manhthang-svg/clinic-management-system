package clinic.backend.utils;

import java.util.Optional;

/**
 * Occupation codes by social group (QĐ 4069/2001/QĐ-BYT, Phụ lục 6), used for {@code nghenghiep_ma} (CV 365).
 * Staff only pick the name; the code is derived from it.
 * Keep the names in sync with OCCUPATIONS in frontend/src/components/patients/patientForm.js.
 */
public final class OccupationCatalog {

    private static final CodeCatalog CATALOG = new CodeCatalog(new String[][] {
            {"01", "Trẻ dưới 6 tuổi đi học, dưới 15 tuổi không đi học"},
            {"02", "Sinh viên, học sinh"},
            {"03", "Hưu và trên 60 tuổi"},
            {"04", "Công nhân"},
            {"05", "Nông dân"},
            {"06", "Lực lượng vũ trang"},
            {"07", "Trí thức"},
            {"08", "Hành chính, sự nghiệp"},
            {"09", "Y tế"},
            {"10", "Dịch vụ"},
            {"11", "Việt kiều"},
            {"12", "Ngoại kiều"},
            {"99", "Khác"},
    });

    private OccupationCatalog() {
    }

    /** The code for a listed name, ignoring case and extra spaces; empty for a name outside the list. */
    public static Optional<String> codeOf(String name) {
        return CATALOG.codeOf(name);
    }
}
