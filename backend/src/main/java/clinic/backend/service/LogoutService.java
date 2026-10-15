package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.model.AuthSession;
import clinic.backend.model.RefreshToken;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.repository.SecurityAuditLog.Outcome;
import clinic.backend.repository.SecurityAuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-2 Logout (docs/use-cases/UC-2-logout.md). Ends only the session of the presented refresh token
 * and is idempotent: unknown, expired or already revoked tokens are accepted silently.
 */
@Service
public class LogoutService {

    private final AuthSessionRepository sessions;
    private final RefreshTokenCodec refreshTokenCodec;
    private final SecurityAuditLog auditLog;
    private final Clock clock;

    public LogoutService(
            AuthSessionRepository sessions,
            RefreshTokenCodec refreshTokenCodec,
            SecurityAuditLog auditLog,
            Clock clock) {

        this.sessions = sessions;
        this.refreshTokenCodec = refreshTokenCodec;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        Optional<AuthSession> session = sessions.findTokenByHash(refreshTokenCodec.digest(rawRefreshToken))
                .map(RefreshToken::sessionId)
                .flatMap(sessions::findSession);
        if (session.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        UUID accountId = session.get().accountId();
        if (sessions.revokeSession(session.get().id(), SessionRevocationReason.LOGOUT, now)) {
            auditLog.record(new SecurityAuditLog.Event(
                    EventType.LOGOUT, accountId, accountId, Outcome.SUCCESS, now));
        }
    }
}
