package clinic.backend.account.application;

import java.time.Clock;

import clinic.backend.account.application.port.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes sessions that ended longer ago than the retention period (docs/security.md, Cleanup).
 */
@Service
public class SessionCleanupService {

    private final AuthSessionRepository sessions;
    private final SessionPolicy sessionPolicy;
    private final Clock clock;

    public SessionCleanupService(AuthSessionRepository sessions, SessionPolicy sessionPolicy, Clock clock) {
        this.sessions = sessions;
        this.sessionPolicy = sessionPolicy;
        this.clock = clock;
    }

    @Transactional
    public int deleteEndedSessions() {
        return sessions.deleteSessionsEndedBefore(clock.instant().minus(sessionPolicy.retentionAfterEnd()));
    }
}
