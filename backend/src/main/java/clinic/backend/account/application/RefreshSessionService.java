package clinic.backend.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import clinic.backend.account.application.AuthenticationResult.Authenticated;
import clinic.backend.account.application.AuthenticationResult.Rejected;
import clinic.backend.account.application.port.AuthSessionRepository;
import clinic.backend.account.application.port.SecurityAuditLog;
import clinic.backend.account.application.port.SecurityAuditLog.EventType;
import clinic.backend.account.application.port.SecurityAuditLog.Outcome;
import clinic.backend.account.application.port.StaffAccountRepository;
import clinic.backend.account.domain.AuthSession;
import clinic.backend.account.domain.RefreshToken;
import clinic.backend.account.domain.SessionRevocationReason;
import clinic.backend.account.domain.StaffAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session refresh with rotation and reuse detection (UC-1, docs/security.md Refresh Tokens).
 */
@Service
public class RefreshSessionService {

    private final AuthSessionRepository sessions;
    private final StaffAccountRepository accounts;
    private final RefreshTokenCodec refreshTokenCodec;
    private final SessionTokenIssuer sessionTokenIssuer;
    private final SecurityAuditLog auditLog;
    private final Clock clock;

    public RefreshSessionService(
            AuthSessionRepository sessions,
            StaffAccountRepository accounts,
            RefreshTokenCodec refreshTokenCodec,
            SessionTokenIssuer sessionTokenIssuer,
            SecurityAuditLog auditLog,
            Clock clock) {

        this.sessions = sessions;
        this.accounts = accounts;
        this.refreshTokenCodec = refreshTokenCodec;
        this.sessionTokenIssuer = sessionTokenIssuer;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public AuthenticationResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return new Rejected();
        }
        Instant now = clock.instant();
        byte[] digest = refreshTokenCodec.digest(rawRefreshToken);

        Optional<RefreshToken> presented = sessions.findTokenByHash(digest);
        if (presented.isEmpty()) {
            return new Rejected();
        }
        RefreshToken token = presented.get();
        AuthSession session = sessions.findSession(token.sessionId()).orElseThrow();

        if (!sessions.markTokenUsedIfUnused(digest, now)) {
            handleReuse(session, now);
            return new Rejected();
        }

        if (token.isExpired(now) || !session.isUsable(now)) {
            return new Rejected();
        }

        Optional<StaffAccount> account = accounts.findById(session.accountId());
        if (account.isEmpty() || !account.get().canAuthenticate()) {
            return new Rejected();
        }

        return new Authenticated(sessionTokenIssuer.issue(account.get(), session, now));
    }

    private void handleReuse(AuthSession session, Instant now) {
        if (sessions.revokeSession(session.id(), SessionRevocationReason.REUSE_DETECTED, now)) {
            auditLog.record(new SecurityAuditLog.Event(
                    EventType.REFRESH_TOKEN_REUSE_DETECTED, null, session.accountId(), Outcome.FAILURE, now));
        }
    }
}
