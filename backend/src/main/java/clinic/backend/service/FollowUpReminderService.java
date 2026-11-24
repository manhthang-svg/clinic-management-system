package clinic.backend.service;

/** UC-75: reminder emails of follow-up appointments. */
public interface FollowUpReminderService {

    /**
     * Sends one reminder email to each patient with an email address whose scheduled appointment falls
     * {@code daysBefore} days from today (clinic time). An appointment is reminded at most once; a failed send is
     * retried by the next run of the same day.
     */
    Result sendDueReminders();

    /**
     * @param sent    emails sent
     * @param failed  emails the SMTP server refused or could not take; retried by the next run
     * @param skipped appointments no longer due when they were locked (cancelled, used or already reminded)
     */
    record Result(int sent, int failed, int skipped) {

        public int total() {
            return sent + failed + skipped;
        }
    }
}
