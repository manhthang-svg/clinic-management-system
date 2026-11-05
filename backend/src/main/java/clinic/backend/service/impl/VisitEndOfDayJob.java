package clinic.backend.service.impl;

import clinic.backend.service.VisitEndOfDayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the end-of-day job after midnight clinic time, so it closes the visits of earlier days only (BR-22). The run
 * time is configurable (Q-32); a missed night is caught up by the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clinic.visit.end-of-day.enabled", havingValue = "true", matchIfMissing = true)
public class VisitEndOfDayJob {

    private final VisitEndOfDayService visitEndOfDayService;

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
}
