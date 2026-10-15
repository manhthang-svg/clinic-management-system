package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import clinic.backend.config.properties.ClinicSecurityProperties;
import clinic.backend.entity.AuthSession;
import clinic.backend.entity.RefreshToken;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.RefreshTokenRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.jwt.JwtUtils;
import clinic.backend.security.jwt.JwtUtils.IssuedAccessToken;
import clinic.backend.service.RefreshTokenService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.service.SessionTokens;
import clinic.backend.utils.TokenUtils;
import clinic.backend.utils.UuidV7Generator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SecurityAuditService securityAuditService;
    private final JwtUtils jwtUtils;
    private final UuidV7Generator ids;
    private final ClinicSecurityProperties.Session sessionSettings;
    private final Clock clock;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            AuthSessionRepository authSessionRepository,
            StaffAccountRepository staffAccountRepository,
            SecurityAuditService securityAuditService,
            JwtUtils jwtUtils,
            UuidV7Generator ids,
            ClinicSecurityProperties properties,
            Clock clock) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.securityAuditService = securityAuditService;
        this.jwtUtils = jwtUtils;
        this.ids = ids;
        this.sessionSettings = properties.session();
        this.clock = clock;
    }

    @Override
    @Transactional
    public SessionTokens issueTokens(StaffAccount account, AuthSession session, Instant now) {
        String rawRefreshToken = TokenUtils.newOpaqueToken();
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(
                ids.next(),
                session.getId(),
                TokenUtils.sha256(rawRefreshToken),
                now,
                session.refreshTokenExpiry(now, sessionSettings.idleTimeout())));

        IssuedAccessToken accessToken = jwtUtils.generateAccessToken(account, session.getId(), now);

        return new SessionTokens(
                accessToken.value(),
                accessToken.expiresAt(),
                rawRefreshToken,
                session.getAbsoluteExpiresAt(),
                account.isPasswordChangeRequired(),
                new SessionTokens.AccountSummary(
                        account.getId(), account.getUsername(), account.getFullName(), account.getRole()));
    }

    @Override
    @Transactional
    public Optional<SessionTokens> rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        byte[] digest = TokenUtils.sha256(rawRefreshToken);

        Optional<RefreshToken> presented = refreshTokenRepository.findByTokenHash(digest);
        if (presented.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = presented.get();
        AuthSession session = authSessionRepository.findById(token.getAuthSessionId()).orElseThrow();

        if (refreshTokenRepository.markUsedIfUnused(digest, now) != 1) {
            handleReuse(session, now);
            return Optional.empty();
        }

        if (token.isExpired(now) || !session.isUsable(now)) {
            return Optional.empty();
        }

        Optional<StaffAccount> account = staffAccountRepository.findById(session.getStaffAccountId());
        if (account.isEmpty() || !account.get().canAuthenticate()) {
            return Optional.empty();
        }

        return Optional.of(issueTokens(account.get(), session, now));
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        Optional<AuthSession> session = refreshTokenRepository.findByTokenHash(TokenUtils.sha256(rawRefreshToken))
                .map(RefreshToken::getAuthSessionId)
                .flatMap(authSessionRepository::findById);
        if (session.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        AuthSession ended = session.get();
        if (authSessionRepository.revoke(ended.getId(), SessionRevocationReason.LOGOUT, now) == 1) {
            securityAuditService.record(
                    SecurityAuditEventType.LOGOUT,
                    ended.getStaffAccountId(),
                    ended.getStaffAccountId(),
                    AuditOutcome.SUCCESS,
                    now);
        }
    }

    @Override
    @Transactional
    public int deleteEndedSessions() {
        return authSessionRepository.deleteEndedBefore(clock.instant().minus(sessionSettings.retentionAfterEnd()));
    }

    private void handleReuse(AuthSession session, Instant now) {
        if (authSessionRepository.revoke(session.getId(), SessionRevocationReason.REUSE_DETECTED, now) == 1) {
            securityAuditService.record(
                    SecurityAuditEventType.REFRESH_TOKEN_REUSE_DETECTED,
                    null,
                    session.getStaffAccountId(),
                    AuditOutcome.FAILURE,
                    now);
        }
    }
}
