package clinic.backend.utils;

import java.util.Optional;

/**
 * Vietnamese ethnic groups and their 2-digit codes as published by the General Statistics Office
 * (nso.gov.vn, "Các dân tộc Việt Nam"), used for {@code dantoc_ma} (CV 365).
 * Staff only pick the name; the code is derived from it so nobody has to remember codes.
 * Keep the names in sync with ETHNICITIES in frontend/src/components/patients/patientForm.js.
 */
public final class EthnicityCatalog {

    private static final CodeCatalog CATALOG = new CodeCatalog(new String[][] {
            {"01", "Kinh"}, {"02", "Tày"}, {"03", "Thái"}, {"04", "Hoa"}, {"05", "Khơ-me"}, {"06", "Mường"},
            {"07", "Nùng"}, {"08", "HMông"}, {"09", "Dao"}, {"10", "Gia-rai"}, {"11", "Ngái"}, {"12", "Ê-đê"},
            {"13", "Ba na"}, {"14", "Xơ-Đăng"}, {"15", "Sán Chay"}, {"16", "Cơ-ho"}, {"17", "Chăm"}, {"18", "Sán Dìu"},
            {"19", "Hrê"}, {"20", "Mnông"}, {"21", "Ra-glai"}, {"22", "Xtiêng"}, {"23", "Bru-Vân Kiều"}, {"24", "Thổ"},
            {"25", "Giáy"}, {"26", "Cơ-tu"}, {"27", "Gié Triêng"}, {"28", "Mạ"}, {"29", "Khơ-mú"}, {"30", "Co"},
            {"31", "Tà-ôi"}, {"32", "Chơ-ro"}, {"33", "Kháng"}, {"34", "Xinh-mun"}, {"35", "Hà Nhì"}, {"36", "Chu ru"},
            {"37", "Lào"}, {"38", "La Chí"}, {"39", "La Ha"}, {"40", "Phù Lá"}, {"41", "La Hủ"}, {"42", "Lự"},
            {"43", "Lô Lô"}, {"44", "Chứt"}, {"45", "Mảng"}, {"46", "Pà Thẻn"}, {"47", "Co Lao"}, {"48", "Cống"},
            {"49", "Bố Y"}, {"50", "Si La"}, {"51", "Pu Péo"}, {"52", "Brâu"}, {"53", "Ơ Đu"}, {"54", "Rơ măm"},
            {"55", "Người nước ngoài"}, {"56", "Không rõ"},
    })
            // name saved before the official spelling was used
            .alias("Khmer", "05");

    private EthnicityCatalog() {
    }

    /** The code for a listed name, ignoring case and extra spaces; empty for a name outside the list. */
    public static Optional<String> codeOf(String name) {
        return CATALOG.codeOf(name);
    }
}
