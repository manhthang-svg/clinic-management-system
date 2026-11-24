package clinic.backend.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;

import clinic.backend.config.properties.FollowUpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * BR-124 Follow-up Validity Period: an appointment is used for one visit, linked when the patient comes from the
 * appointment date to the configured number of working days after it (default 10; Saturday and Sunday are not
 * counted; public holidays are not excluded automatically). Before or after that period the patient is registered as
 * a normal visit.
 */
@Component
@RequiredArgsConstructor
public class FollowUpValidity {

    private final FollowUpProperties properties;

    /** The last day the appointment can be linked to a visit. */
    public LocalDate validUntil(LocalDate appointmentDate) {
        LocalDate day = appointmentDate;
        int remaining = properties.validityWorkingDays();
        while (remaining > 0) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return day;
    }

    /** The appointment can be linked to a visit registered on {@code day}. */
    public boolean usableOn(LocalDate appointmentDate, LocalDate day) {
        return !day.isBefore(appointmentDate) && !day.isAfter(validUntil(appointmentDate));
    }
}
