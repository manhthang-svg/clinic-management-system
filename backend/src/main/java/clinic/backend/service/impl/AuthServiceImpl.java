package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import clinic.backend.config.properties.ClinicSecurityProperties;
import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.response.SessionResponse;
import clinic.backend.entity.AuthSession;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.SessionMapper;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.RequestOriginVerifier;
import clinic.backend.service.AuthService;
import clinic.backend.service.RefreshTokenService;
import clinic.backend.service.SecurityAuditService;
import clinic.backend.service.SessionTokens;
import clinic.backend.utils.CookieUtils;
import clinic.backend.utils.UuidV7Generator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final StaffAccountRepository staffAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService securityAuditService;
    private final PasswordHasher passwordHasher;
    private final RequestOriginVerifier requestOriginVerifier;
    private final CookieUtils cookieUtils;
    private final SessionMapper sessionMapper;
    private final UuidV7Generator ids;
    private final ClinicSecurityProperties.Session sessionSettings;
    private final Clock clock;

    public AuthServiceImpl(
            StaffAccountRepository staffAccountRepository,
            AuthSessionRepository authSessionRepository,
            RefreshTokenService refreshTokenService,
            SecurityAuditService securityAuditService,
            PasswordHasher passwordHasher,
            RequestOriginVerifier requestOriginVerifier,
            CookieUtils cookieUtils,
            SessionMapper sessionMapper,
            UuidV7Generator ids,
            ClinicSecurityProperties properties,
            Clock clock) {

        this.staffAccountRepository = staffAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenService = refreshTokenService;
        this.securityAuditService = securityAuditService;
        this.passwordHasher = passwordHasher;
        this.requestOriginVerifier = requestOriginVerifier;
        this.cookieUtils = cookieUtils;
        this.sessionMapper = sessionMapper;
        this.ids = ids;
        this.sessionSettings = properties.session();
        this.clock = clock;
    }

    /**
     * Rejections are thrown as {@link AppException} but do not roll back, so failed-login counters and audit
     * events are committed.
     */
    @Override
    @Transactional(noRollbackFor = AppException.class)
    public SessionResponse login(LoginRequest request, HttpServletResponse response) {
        Instant now = clock.instant();
        String password = request.password();

        Optional<StaffAccount> candidate = StaffAccount.normalizeUsername(request.username())
                .flatMap(staffAccountRepository::findByUsernameForAuthentication);
        if (candidate.isEmpty()) {
            passwordHasher.simulateMatch(password);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        StaffAccount account = candidate.get();
        if (account.isLoginBlocked(now)) {
            passwordHasher.simulateMatch(password);
            auditFailedLogin(SecurityAuditEventType.LOGIN_FAILED, account, now);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordHasher.matches(password, account.getPasswordHash())) {
            boolean blockStarted = account.registerFailedLogin(now);
            staffAccountRepository.saveAndFlush(account);
            auditFailedLogin(SecurityAuditEventType.LOGIN_FAILED, account, now);
            if (blockStarted) {
                auditFailedLogin(SecurityAuditEventType.LOGIN_THROTTLED, account, now);
            }
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!account.canAuthenticate()) {
            auditFailedLogin(SecurityAuditEventType.LOGIN_FAILED, account, now);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        account.registerSuccessfulLogin(now);
        StaffAccount saved = staffAccountRepository.saveAndFlush(account);

        AuthSession session = authSessionRepository.saveAndFlush(
                AuthSession.start(ids.next(), saved.getId(), now, sessionSettings.absoluteLifetime()));
        SessionTokens tokens = refreshTokenService.issueTokens(saved, session, now);

        securityAuditService.record(
                SecurityAuditEventType.LOGIN_SUCCEEDED, saved.getId(), saved.getId(), AuditOutcome.SUCCESS, now);
        return sessionResponse(tokens, response);
    }

    @Override
    public SessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        verifyOrigin(request);

        Optional<SessionTokens> tokens =
                refreshTokenService.rotate(cookieUtils.readRefreshToken(request).orElse(null));
        if (tokens.isEmpty()) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.clearRefreshTokenCookie().toString());
            throw new AppException(ErrorCode.SESSION_INVALID);
        }
        return sessionResponse(tokens.get(), response);
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        verifyOrigin(request);

        refreshTokenService.revoke(cookieUtils.readRefreshToken(request).orElse(null));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.clearRefreshTokenCookie().toString());
    }

    /**
     * Targeted CSRF protection for the endpoints that authenticate through the refresh-token cookie.
     */
    private void verifyOrigin(HttpServletRequest request) {
        if (!requestOriginVerifier.isAllowed(request)) {
            throw new AppException(ErrorCode.ORIGIN_NOT_ALLOWED);
        }
    }

    private SessionResponse sessionResponse(SessionTokens tokens, HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtils.buildRefreshTokenCookie(tokens.refreshToken(), tokens.sessionExpiresAt()).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return sessionMapper.toSessionResponse(tokens);
    }

    private void auditFailedLogin(SecurityAuditEventType type, StaffAccount account, Instant now) {
        securityAuditService.record(type, null, account.getId(), AuditOutcome.FAILURE, now);
    }
}
