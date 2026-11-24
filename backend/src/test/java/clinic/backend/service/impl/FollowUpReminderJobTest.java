package clinic.backend.service.impl;

import clinic.backend.service.FollowUpReminderService;
import clinic.backend.service.FollowUpReminderService.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpReminderJobTest {

    @Mock
    private FollowUpReminderService service;

    @Test
    void sendsTheDueReminders() {
        when(service.sendDueReminders()).thenReturn(new Result(2, 1, 0));

        new FollowUpReminderJob(service).sendReminders();

        verify(service).sendDueReminders();
    }

    @Test
    void aFailedRunDoesNotStopTheScheduler() {
        when(service.sendDueReminders()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> new FollowUpReminderJob(service).sendReminders()).doesNotThrowAnyException();
    }
}
