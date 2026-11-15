package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;

import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-61 BR-61-02 (BR-108). */
class FollowUpRulesTest {

    private static final LocalDate VISIT = LocalDate.of(2026, 11, 3);

    @Test
    void takesTheVisitDateInTheClinicTimeZone() {
        // 18:00 UTC on 2 November is 01:00 on 3 November in Ho Chi Minh City.
        assertThat(FollowUpRules.visitDate(Instant.parse("2026-11-02T18:00:00Z"))).isEqualTo(VISIT);
    }

    @Test
    void acceptsTheDayAfterTheVisitUpTo365Days() {
        assertThat(FollowUpRules.requireDate(VISIT.plusDays(1), VISIT)).isEqualTo(VISIT.plusDays(1));
        assertThat(FollowUpRules.requireDate(VISIT.plusDays(365), VISIT)).isEqualTo(VISIT.plusDays(365));
        assertThat(FollowUpRules.minDate(VISIT)).isEqualTo(VISIT.plusDays(1));
        assertThat(FollowUpRules.maxDate(VISIT)).isEqualTo(VISIT.plusDays(365));
    }

    @Test
    void refusesAMissingDateTheVisitDayAndADateBeyond365Days() {
        assertReason(() -> FollowUpRules.requireDate(null, VISIT), "followUpDate", "REQUIRED");
        assertReason(() -> FollowUpRules.requireDate(VISIT, VISIT), "followUpDate", "NOT_AFTER_VISIT");
        assertReason(() -> FollowUpRules.requireDate(VISIT.plusDays(366), VISIT), "followUpDate", "TOO_FAR");
    }

    @Test
    void trimsTheAdviceAndRefusesMoreThan2000Characters() {
        assertThat(FollowUpRules.advice("  Đo huyết áp mỗi sáng  ")).isEqualTo("Đo huyết áp mỗi sáng");
        assertThat(FollowUpRules.advice("   ")).isNull();
        assertThat(FollowUpRules.advice("a".repeat(2000))).hasSize(2000);
        assertReason(() -> FollowUpRules.advice("a".repeat(2001)), "advice", "TOO_LONG");
    }

    private static void assertReason(Runnable call, String field, String reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AppException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_UP_APPOINTMENT_INVALID);
            assertThat(error.getDetails()).containsEntry("field", field).containsEntry("reason", reason);
        });
    }
}
