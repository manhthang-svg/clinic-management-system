package clinic.backend.enums;

/**
 * Why a visit is a priority case (BR-14): the legal priority groups that fit this clinic, which takes no
 * emergency cases. ELDERLY and FRAIL_DISABLED are legacy values kept for visits recorded before V46.
 */
public enum PriorityReason {
    CHILD_UNDER_6("Trẻ em dưới 06 tuổi"),
    PREGNANT("Phụ nữ có thai"),
    EXTREME_DISABILITY("Người khuyết tật đặc biệt nặng"),
    SEVERE_DISABILITY("Người khuyết tật nặng"),
    AGED_75_PLUS("Người từ đủ 75 tuổi trở lên"),
    MERITORIOUS("Người có công với cách mạng"),
    ELDERLY("Người cao tuổi", true),
    FRAIL_DISABLED("Người suy kiệt / khuyết tật", true);

    private final String label;
    private final boolean legacy;

    PriorityReason(String label) {
        this(label, false);
    }

    PriorityReason(String label, boolean legacy) {
        this.label = label;
        this.legacy = legacy;
    }

    /** Vietnamese label printed on tickets and lists. */
    public String label() {
        return label;
    }

    /** A legacy reason can still be read but is no longer chosen for a new visit. */
    public boolean isLegacy() {
        return legacy;
    }
}
