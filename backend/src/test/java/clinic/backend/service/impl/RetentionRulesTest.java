package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionRulesTest {

    @Test
    void countsYearsFromTheClosingYearToItsLastDay() {
        assertThat(RetentionRules.expiresOn(Instant.parse("2026-11-17T03:00:00Z"), (short) 10))
                .isEqualTo(LocalDate.of(2036, 12, 31));
        assertThat(RetentionRules.expiresOn(Instant.parse("2026-01-01T00:00:00Z"), (short) 20))
                .isEqualTo(LocalDate.of(2046, 12, 31));
    }

    @Test
    void usesTheClosingYearInVietnamTime() {
        // 31/12/2026 17:30 UTC is 01/01/2027 00:30 in Asia/Ho_Chi_Minh.
        assertThat(RetentionRules.expiresOn(Instant.parse("2026-12-31T17:30:00Z"), (short) 10))
                .isEqualTo(LocalDate.of(2037, 12, 31));
    }

    @Test
    void isNeverEarlierThanTheClosingDatePlusTheYears() {
        Instant closedAt = Instant.parse("2026-12-30T10:00:00Z");
        assertThat(RetentionRules.expiresOn(closedAt, (short) 10)).isAfterOrEqualTo(LocalDate.of(2036, 12, 30));
    }
}
