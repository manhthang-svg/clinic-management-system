package clinic.backend.config;

import clinic.backend.service.SessionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cleanup of ended login sessions (docs/security.md, Cleanup).
 */
@Component
class SessionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final SessionCleanupService cleanupService;

    SessionCleanupScheduler(SessionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${clinic.security.session.cleanup-cron:0 30 3 * * *}")
    void deleteEndedSessions() {
        int deleted = cleanupService.deleteEndedSessions();
        if (deleted > 0) {
            LOGGER.info("Deleted ended login sessions [count={}]", deleted);
        }
    }
}
