package clinic.backend.service.impl;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowUpReminderEmailTest {

    @Test
    void carriesTheAppointmentAndTheClinicInVietnamese() {
        FollowUpReminderEmail email = FollowUpReminderEmail.of(
                " Nguyễn Văn An ", LocalDate.of(2026, 12, 1), "GH-DK0000000123", "Trần Thị Bình",
                "Phòng khám Đa khoa An Tâm", "12 Lê Lợi, Hà Nội", "0241234567");

        assertThat(email.subject()).isEqualTo("Nhắc lịch tái khám ngày 01/12/2026 - Phòng khám Đa khoa An Tâm");
        assertThat(email.body())
                .startsWith("Kính gửi Nguyễn Văn An,")
                .contains("- Ngày tái khám: 01/12/2026", "- Số giấy hẹn: GH-DK0000000123",
                        "- Bác sĩ hẹn: Trần Thị Bình", "- Địa chỉ: 12 Lê Lợi, Hà Nội", "- Điện thoại: 0241234567",
                        "trước ngày hẹn nếu có dấu hiệu (triệu chứng) bất thường",
                        "Đây là email tự động");
    }

    @Test
    void leavesOutMissingLinesAndFallsBackToAGenericClinicName() {
        FollowUpReminderEmail email = FollowUpReminderEmail.of(
                "Lê Văn C", LocalDate.of(2026, 12, 1), null, " ", null, null, null);

        assertThat(email.subject()).isEqualTo("Nhắc lịch tái khám ngày 01/12/2026 - Phòng khám");
        assertThat(email.body()).doesNotContain("Số giấy hẹn", "Bác sĩ hẹn", "Địa chỉ:", "Điện thoại:");
    }
}
