package clinic.backend.service.impl;

import clinic.backend.service.FollowUpAppointmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BR-124: together with the end-of-day job (same schedule), marks "Lỡ hẹn" the follow-up appointments whose validity
 * period has ended. Also runs once at startup to catch up on a missed run; marking is idempotent.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "clinic.visit.end-of-day.enabled", havingValue = "true", matchIfMissing = true)
public class FollowUpExpiryJob {

    private final FollowUpAppointmentService followUpAppointmentService;
    private final boolean runOnStartup;

    public FollowUpExpiryJob(
            FollowUpAppointmentService followUpAppointmentService,
            @Value("${clinic.visit.end-of-day.run-on-startup:true}") boolean runOnStartup) {
        this.followUpAppointmentService = followUpAppointmentService;
        this.runOnStartup = runOnStartup;
    }

    @Scheduled(cron = "${clinic.visit.end-of-day.cron:0 5 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void markExpiredAppointments() {
        int marked = followUpAppointmentService.markExpiredAsMissed();
        if (marked > 0) {
            log.info("Follow-up expiry job marked appointments as missed [count={}]", marked);
        }
    }

    /** Catches up on a missed scheduled run. A failure is logged and never stops the application. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (!runOnStartup) {
            return;
        }
        try {
            markExpiredAppointments();
        } catch (RuntimeException ex) {
            log.warn("Follow-up expiry catch-up at startup failed; the scheduled run will retry", ex);
        }
    }
}
