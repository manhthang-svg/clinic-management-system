package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import clinic.backend.config.properties.FollowUpProperties;
import org.junit.jupiter.api.Test;

class FollowUpValidityTest {

    private final FollowUpValidity validity = new FollowUpValidity(new FollowUpProperties(10));

    @Test
    void countsTenWorkingDaysAfterTheAppointmentSkippingWeekends() {
        // Monday 2026-11-02 + 10 working days = Monday 2026-11-16.
        assertThat(validity.validUntil(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 16));
        // Friday 2026-11-06 + 10 working days = Friday 2026-11-20.
        assertThat(validity.validUntil(LocalDate.of(2026, 11, 6))).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void isUsableOnlyFromTheAppointmentDateToTheEndOfThePeriod() {
        LocalDate appointment = LocalDate.of(2026, 11, 2);

        assertThat(validity.usableOn(appointment, LocalDate.of(2026, 11, 1))).isFalse();
        assertThat(validity.usableOn(appointment, appointment)).isTrue();
        assertThat(validity.usableOn(appointment, LocalDate.of(2026, 11, 16))).isTrue();
        assertThat(validity.usableOn(appointment, LocalDate.of(2026, 11, 17))).isFalse();
    }

    @Test
    void zeroWorkingDaysAllowsOnlyTheAppointmentDate() {
        FollowUpValidity sameDay = new FollowUpValidity(new FollowUpProperties(0));

        assertThat(sameDay.validUntil(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 2));
    }
}
