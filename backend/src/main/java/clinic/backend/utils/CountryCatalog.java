package clinic.backend.utils;

import java.util.Optional;

/**
 * Country codes (QĐ 4069/2001/QĐ-BYT, Phụ lục 7), used for {@code ngoaikieu_ma} (CV 365).
 * Staff only pick the country; the code is derived from it.
 * Keep the names in sync with COUNTRIES in frontend/src/components/patients/patientForm.js.
 */
public final class CountryCatalog {

    private static final CodeCatalog CATALOG = new CodeCatalog(new String[][] {
            {"01", "Anh"},
            {"02", "Brunei"},
            {"03", "Campuchia"},
            {"04", "Canada"},
            {"05", "Cuba"},
            {"06", "Đài Loan"},
            {"07", "Đức"},
            {"08", "Hà Lan"},
            {"09", "Hàn Quốc"},
            {"10", "Hoa Kỳ"},
            {"11", "Indonesia"},
            {"12", "Lào"},
            {"13", "Malaysia"},
            {"14", "Myanmar"},
            {"15", "Nga"},
            {"16", "Nhật"},
            {"17", "Pháp"},
            {"18", "Philippines"},
            {"19", "Singapore"},
            {"20", "Thái Lan"},
            {"21", "Trung Quốc"},
            {"99", "Khác"},
    }).alias("Cu ba", "05");

    private CountryCatalog() {
    }

    /** The code for a listed country, ignoring case and extra spaces; empty for a name outside the list. */
    public static Optional<String> codeOf(String name) {
        return CATALOG.codeOf(name);
    }
}
