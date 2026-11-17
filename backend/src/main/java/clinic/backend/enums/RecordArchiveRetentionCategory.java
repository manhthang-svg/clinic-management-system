package clinic.backend.enums;

/**
 * Retention category of an archived medical record, chosen by the Doctor when closing it (UC-58 BR-58-06), with
 * the minimum retention period of TT 33/2025/TT-BYT Phụ lục.
 */
public enum RecordArchiveRetentionCategory {
    /** Dòng 44: hồ sơ bệnh án nội trú, ngoại trú. */
    STANDARD(10),
    /** Dòng 40: tai nạn lao động. */
    WORK_ACCIDENT(20),
    /** Dòng 40: tai nạn giao thông. */
    TRAFFIC_ACCIDENT(20),
    /** Dòng 40: tâm thần. */
    PSYCHIATRIC(20);

    private final short retentionYears;

    RecordArchiveRetentionCategory(int retentionYears) {
        this.retentionYears = (short) retentionYears;
    }

    public short retentionYears() {
        return retentionYears;
    }
}
