package clinic.backend.service.impl;

import clinic.backend.service.VisitEndOfDayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the end-of-day job after midnight clinic time, so it closes the visits of earlier days only (BR-22). The run
 * time is configurable (Q-32). It also runs once at startup, so a machine that was off at the scheduled time (a
 * developer laptop, a restarted server) catches up; closing is idempotent, so a run that finds nothing is harmless.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "clinic.visit.end-of-day.enabled", havingValue = "true", matchIfMissing = true)
public class VisitEndOfDayJob {

    private final VisitEndOfDayService visitEndOfDayService;
    private final boolean runOnStartup;

    public VisitEndOfDayJob(
            VisitEndOfDayService visitEndOfDayService,
            @Value("${clinic.visit.end-of-day.run-on-startup:true}") boolean runOnStartup) {
        this.visitEndOfDayService = visitEndOfDayService;
        this.runOnStartup = runOnStartup;
    }

    @Scheduled(cron = "${clinic.visit.end-of-day.cron:0 5 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void closeUnfinishedVisits() {
        VisitEndOfDayService.Result result = visitEndOfDayService.closeUnfinishedVisits();
        if (result.total() > 0) {
            log.info("End-of-day job closed visits [cancelled={}, abandoned={}, deferred={}]",
                    result.cancelled(), result.abandoned(), result.deferred());
        }
        if (result.deferred() > 0) {
            log.warn("End-of-day job deferred visits to the next run [count={}]", result.deferred());
        }
    }

    /** Catches up on a missed scheduled run. A failure is logged and never stops the application. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (!runOnStartup) {
            return;
        }
        try {
            closeUnfinishedVisits();
        } catch (RuntimeException ex) {
            log.warn("End-of-day catch-up at startup failed; the scheduled run will retry", ex);
        }
    }
}
