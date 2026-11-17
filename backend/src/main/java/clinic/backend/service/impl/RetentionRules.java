package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * UC-58 BR-58-06 expiry date. Luật Lưu trữ 2024 Điều 15 khoản 3 counts the retention period in years "kể từ năm
 * kết thúc công việc", so the period ends on 31/12 of (closing year + years). That date is never earlier than
 * BR-44's "ngày đóng hồ sơ cộng số năm lưu trữ".
 */
final class RetentionRules {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private RetentionRules() {
    }

    static LocalDate expiresOn(Instant closedAt, short retentionYears) {
        int closingYear = LocalDate.ofInstant(closedAt, CLINIC_ZONE).getYear();
        return LocalDate.of(closingYear + retentionYears, 12, 31);
    }
}
