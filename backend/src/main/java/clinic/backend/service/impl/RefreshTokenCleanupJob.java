package clinic.backend.service.impl;

import clinic.backend.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cleanup of ended login sessions (docs/security.md, Cleanup).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${clinic.security.session.cleanup-cron:0 30 3 * * *}")
    public void deleteEndedSessions() {
        int deleted = refreshTokenService.deleteEndedSessions();
        if (deleted > 0) {
            log.info("Deleted ended login sessions [count={}]", deleted);
        }
    }
}
