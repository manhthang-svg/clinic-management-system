package clinic.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UC-75 reminder emails of follow-up appointments, sent over a free SMTP server (Gmail with an App Password in a
 * deployment, Mailpit on a developer machine). The SMTP server itself is configured by {@code spring.mail.*}.
 *
 * @param enabled    whether the reminder job runs; off by default, so no email leaves a machine without SMTP
 * @param daysBefore how many days before the follow-up date the reminder is sent (**ASSUMPTION**: 1)
 * @param from       sender address; Gmail replaces it with the signed-in account, so set it to that account
 */
@ConfigurationProperties(prefix = "clinic.mail.follow-up-reminder")
public record FollowUpReminderProperties(boolean enabled, int daysBefore, String from) {

    public FollowUpReminderProperties {
        if (daysBefore < 1) {
            daysBefore = 1;
        }
    }
}
