package clinic.backend.account.application;

import java.time.Instant;

import clinic.backend.account.application.port.AccessTokenIssuer;
import clinic.backend.account.application.port.AccessTokenIssuer.IssuedAccessToken;
import clinic.backend.account.application.port.AuthSessionRepository;
import clinic.backend.account.domain.AuthSession;
import clinic.backend.account.domain.RefreshToken;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.shared.id.UuidV7Generator;
import org.springframework.stereotype.Component;

/**
 * Issues a new refresh token for a session plus a fresh access token. Callers own the transaction.
 */
@Component
class SessionTokenIssuer {

    private final AuthSessionRepository sessions;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenCodec refreshTokenCodec;
    private final UuidV7Generator ids;
    private final SessionPolicy sessionPolicy;

    SessionTokenIssuer(
            AuthSessionRepository sessions,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenCodec refreshTokenCodec,
            UuidV7Generator ids,
            SessionPolicy sessionPolicy) {

        this.sessions = sessions;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenCodec = refreshTokenCodec;
        this.ids = ids;
        this.sessionPolicy = sessionPolicy;
    }

    SessionTokens issue(StaffAccount account, AuthSession session, Instant now) {
        RefreshTokenCodec.GeneratedToken refreshToken = refreshTokenCodec.generate();
        sessions.saveToken(RefreshToken.issue(
                ids.next(),
                session.id(),
                refreshToken.digest(),
                now,
                session.refreshTokenExpiry(now, sessionPolicy.idleTimeout())));

        IssuedAccessToken accessToken = accessTokenIssuer.issue(account, session.id(), now);

        return new SessionTokens(
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.raw(),
                session.absoluteExpiresAt(),
                account.passwordChangeRequired(),
                new SessionTokens.AccountSummary(
                        account.id(), account.username().value(), account.fullName(), account.role()));
    }
}
