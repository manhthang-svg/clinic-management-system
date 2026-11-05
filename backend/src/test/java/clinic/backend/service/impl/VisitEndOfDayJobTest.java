package clinic.backend.service.impl;

import clinic.backend.service.VisitEndOfDayService;
import clinic.backend.service.VisitEndOfDayService.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitEndOfDayJobTest {

    @Mock
    private VisitEndOfDayService service;

    @Test
    void catchesUpOnStartupForAMachineThatWasOffAtMidnight() {
        when(service.closeUnfinishedVisits()).thenReturn(new Result(1, 2, 0));

        new VisitEndOfDayJob(service, true).catchUpOnStartup();

        verify(service).closeUnfinishedVisits();
    }

    @Test
    void skipsTheStartupRunWhenDisabled() {
        new VisitEndOfDayJob(service, false).catchUpOnStartup();

        verify(service, never()).closeUnfinishedVisits();
    }

    @Test
    void aFailedStartupRunDoesNotStopTheApplication() {
        when(service.closeUnfinishedVisits()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> new VisitEndOfDayJob(service, true).catchUpOnStartup()).doesNotThrowAnyException();
    }
}
