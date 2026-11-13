package clinic.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * UC-59 prescription form. The service checks every value so each failure maps to a field or a line;
 * {@code version} is the version the Doctor loaded (optimistic locking).
 *
 * @param notes          Lưu ý on the prescription (prescriptions.luu_y, up to 2,000 characters)
 * @param advice         Lời dặn on the prescription (prescriptions.loi_dan, up to 2,000 characters)
 */
public record SavePrescriptionRequest(
        List<Line> lines,
        String notes,
        String advice,
        Long version
) {

    /**
     * One medicine line (prescription_details).
     *
     * @param quantity    total quantity to buy in the dispensing unit, a whole number from 1 to 9,999
     * @param dosePerTime Liều mỗi lần, e.g. "1 viên" (up to 50 characters)
     * @param timesPerDay Số lần/ngày, 1 to 24
     * @param days        Số ngày dùng, 1 to 90; more than 30 only for a long-term disease (TT 26/2025)
     * @param usage       Cách dùng, e.g. "Uống sau ăn" (up to 255 characters)
     * @param note        Ghi chú of the line
     */
    public record Line(
            UUID medicineId,
            BigDecimal quantity,
            String dosePerTime,
            Short timesPerDay,
            Short days,
            String usage,
            String note
    ) {
    }
}
