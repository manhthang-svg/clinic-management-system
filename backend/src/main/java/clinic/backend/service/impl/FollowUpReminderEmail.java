package clinic.backend.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * UC-75: subject and plain-text body of a follow-up reminder. It carries no diagnosis or advice: an email is not a
 * protected channel (**ASSUMPTION**, docs/security.md privacy); the patient reads those on the paper slip.
 */
record FollowUpReminderEmail(String subject, String body) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String DEFAULT_CLINIC = "Phòng khám";

    static FollowUpReminderEmail of(
            String patientName, LocalDate followUpDate, String slipNumber, String doctorName,
            String clinicName, String clinicAddress, String clinicPhone) {
        String clinic = blank(clinicName) ? DEFAULT_CLINIC : clinicName.strip();
        String date = DATE.format(followUpDate);
        StringBuilder body = new StringBuilder()
                .append("Kính gửi ").append(patientName.strip()).append(",\n\n")
                .append(clinic).append(" xin nhắc Quý khách lịch hẹn tái khám:\n")
                .append("- Ngày tái khám: ").append(date).append('\n');
        line(body, "Số giấy hẹn", slipNumber);
        line(body, "Bác sĩ hẹn", doctorName);
        line(body, "Địa chỉ", clinicAddress);
        line(body, "Điện thoại", clinicPhone);
        body.append('\n')
                .append("Vui lòng mang theo phiếu hẹn khám lại, thẻ BHYT (nếu có) và giấy tờ tùy thân khi đến khám. ")
                .append("Quý khách có thể đến khám trước ngày hẹn nếu có dấu hiệu (triệu chứng) bất thường.\n\n")
                .append("Đây là email tự động, vui lòng không trả lời email này.\n")
                .append(clinic);
        return new FollowUpReminderEmail("Nhắc lịch tái khám ngày " + date + " - " + clinic, body.toString());
    }

    private static void line(StringBuilder body, String label, String value) {
        if (!blank(value)) {
            body.append("- ").append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
