package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import clinic.backend.model.AuthSession;
import clinic.backend.model.RefreshToken;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.model.StaffAccount;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.repository.SecurityAuditLog.Outcome;
import clinic.backend.repository.SecurityAuditLog;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.AuthenticationResult.Authenticated;
import clinic.backend.service.AuthenticationResult.Rejected;
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
