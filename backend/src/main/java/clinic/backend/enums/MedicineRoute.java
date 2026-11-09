package clinic.backend.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Route of administration of a medicine, chosen from a fixed list (I1-D46). The Vietnamese label is what the
 * catalog stores in {@code duongdung} and what prescriptions copy.
 */
public enum MedicineRoute {
    UONG("Uống"),
    TIEM("Tiêm"),
    BOI_NGOAI_DA("Bôi ngoài da"),
    NHO_MAT("Nhỏ mắt"),
    NHO_MUI("Nhỏ mũi"),
    XIT_MUI("Xịt mũi"),
    KHI_DUNG("Khí dung"),
    DAT_HAU_MON("Đặt hậu môn"),
    DAT_AM_DAO("Đặt âm đạo"),
    NGAM_DUOI_LUOI("Ngậm dưới lưỡi"),
    DAN_NGOAI_DA("Dán ngoài da"),
    KHAC("Khác");

    private final String label;

    MedicineRoute(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The route whose label is exactly {@code label}. */
    public static Optional<MedicineRoute> fromLabel(String label) {
        return Arrays.stream(values()).filter(route -> route.label.equals(label)).findFirst();
    }
}
