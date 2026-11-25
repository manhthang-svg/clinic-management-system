package clinic.backend.service.impl;

import clinic.backend.service.FollowUpReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-75: sends the reminder emails of the follow-up appointments due in {@code daysBefore} days. It runs several
 * times a day (clinic time) so a send that failed is retried the same day; an appointment is reminded only once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clinic.mail.follow-up-reminder.enabled", havingValue = "true")
public class FollowUpReminderJob {

    private final FollowUpReminderService reminderService;

    @Scheduled(cron = "${clinic.mail.follow-up-reminder.cron:0 0 8,14,20 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void sendReminders() {
        try {
            FollowUpReminderService.Result result = reminderService.sendDueReminders();
            if (result.total() > 0) {
                log.info("Follow-up reminder job finished [sent={}, failed={}, skipped={}]",
                        result.sent(), result.failed(), result.skipped());
            }
        } catch (RuntimeException ex) {
            log.warn("Follow-up reminder job failed; the next scheduled run retries it", ex);
        }
    }
}
