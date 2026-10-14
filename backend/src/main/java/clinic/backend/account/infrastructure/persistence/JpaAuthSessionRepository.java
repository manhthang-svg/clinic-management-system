package clinic.backend.account.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.account.application.port.AuthSessionRepository;
import clinic.backend.account.domain.AuthSession;
import clinic.backend.account.domain.RefreshToken;
import clinic.backend.account.domain.SessionRevocationReason;
import org.springframework.stereotype.Repository;

@Repository
class JpaAuthSessionRepository implements AuthSessionRepository {

    private final AuthSessionJpaRepository sessions;
    private final RefreshTokenJpaRepository tokens;

    JpaAuthSessionRepository(AuthSessionJpaRepository sessions, RefreshTokenJpaRepository tokens) {
        this.sessions = sessions;
        this.tokens = tokens;
    }

    @Override
    public void saveSession(AuthSession session) {
        sessions.saveAndFlush(new AuthSessionEntity(
                session.id(),
                session.accountId(),
                session.createdAt(),
                session.absoluteExpiresAt(),
                session.revokedAt(),
                session.revocationReason()));
    }

    @Override
    public Optional<AuthSession> findSession(UUID sessionId) {
        return sessions.findById(sessionId).map(entity -> new AuthSession(
                entity.getId(),
                entity.getStaffAccountId(),
                entity.getCreatedAt(),
                entity.getAbsoluteExpiresAt(),
                entity.getRevokedAt(),
                entity.getRevocationReason()));
    }

    @Override
    public void saveToken(RefreshToken token) {
        tokens.saveAndFlush(new RefreshTokenEntity(
                token.id(),
                token.sessionId(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                token.usedAt()));
    }

    @Override
    public Optional<RefreshToken> findTokenByHash(byte[] tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(entity -> new RefreshToken(
                entity.getId(),
                entity.getAuthSessionId(),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getUsedAt()));
    }

    @Override
    public boolean markTokenUsedIfUnused(byte[] tokenHash, Instant now) {
        return tokens.markUsedIfUnused(tokenHash, now) == 1;
    }

    @Override
    public boolean revokeSession(UUID sessionId, SessionRevocationReason reason, Instant now) {
        return sessions.revoke(sessionId, reason, now) == 1;
    }

    @Override
    public int deleteSessionsEndedBefore(Instant cutoff) {
        return sessions.deleteEndedBefore(cutoff);
    }
}
