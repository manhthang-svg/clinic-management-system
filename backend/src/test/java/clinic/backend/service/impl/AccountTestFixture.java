package clinic.backend.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.ClinicSecurityProperties;
import clinic.backend.entity.AuthSession;
import clinic.backend.entity.AuditLog;
import clinic.backend.entity.RefreshToken;
import clinic.backend.entity.SecurityAuditEvent;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.mapper.SessionMapper;
import clinic.backend.mapper.ProfileMapper;
import clinic.backend.repository.AuthSessionRepository;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.RefreshTokenRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.SecurityAuditEventRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.security.RequestOriginVerifier;
import clinic.backend.security.TemporaryPasswordGenerator;
import clinic.backend.security.jwt.JwtUtils;
import clinic.backend.security.jwt.JwtUtils.IssuedAccessToken;
import clinic.backend.service.SessionTokens;
import clinic.backend.utils.CookieUtils;
import clinic.backend.utils.TokenUtils;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.factory.Mappers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Wires the real services to repositories backed by in-memory maps, so tests can run whole flows
 * (login, refresh, change password, logout) without a database.
 */
final class AccountTestFixture {

    static final Instant START = Instant.parse("2026-10-14T07:00:00Z");
    static final String FRONTEND_ORIGIN = "http://localhost:5173";
    static final String COOKIE = "clinic_refresh";
    static final ClinicSecurityProperties PROPERTIES = new ClinicSecurityProperties(
            FRONTEND_ORIGIN,
            new ClinicSecurityProperties.Jwt(
                    "unused", "clinic-backend", "clinic-web", Duration.ofMinutes(15), Duration.ofSeconds(30)),
            new ClinicSecurityProperties.Session(
                    Duration.ofHours(2), Duration.ofHours(12), Duration.ofDays(30), COOKIE, true),
            new ClinicSecurityProperties.Password(4));

    final MutableClock clock = new MutableClock(START);
    final UuidV7Generator ids = new UuidV7Generator(clock, new SecureRandom());

    final Map<UUID, StaffAccount> accounts = new HashMap<>();
    final Map<UUID, AuthSession> sessions = new HashMap<>();
    final List<RefreshToken> tokens = new ArrayList<>();
    final List<SecurityAuditEvent> auditEvents = new ArrayList<>();
    final List<AuditLog> businessAuditLogs = new ArrayList<>();

    final StaffAccountRepository staffAccountRepository = staffAccountRepository();
    final StaffRoomAssignmentRepository staffRoomAssignmentRepository = mock(StaffRoomAssignmentRepository.class);
    final RoomRepository roomRepository = mock(RoomRepository.class);
    final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    final AuthSessionRepository authSessionRepository = authSessionRepository();
    final AuditLogRepository auditLogRepository = auditLogRepository();
    final RefreshTokenRepository refreshTokenRepository = refreshTokenRepository();
    final SecurityAuditEventRepository securityAuditEventRepository = securityAuditEventRepository();

    final PasswordHasher passwordHasher = spy(new PasswordHasher(PROPERTIES));
    final TemporaryPasswordGenerator temporaryPasswordGenerator = new TemporaryPasswordGenerator(new SecureRandom());
    final SecurityAuditServiceImpl securityAuditService =
            new SecurityAuditServiceImpl(securityAuditEventRepository, ids);
    final AuditLogServiceImpl auditLogService = new AuditLogServiceImpl(auditLogRepository, new ObjectMapper(), ids);
    final RefreshTokenServiceImpl refreshTokenService = new RefreshTokenServiceImpl(
            refreshTokenRepository, authSessionRepository, staffAccountRepository, securityAuditService,
            jwtUtils(), ids, PROPERTIES, clock);

    AuthServiceImpl authService() {
        return new AuthServiceImpl(
                staffAccountRepository, authSessionRepository, refreshTokenService, securityAuditService,
                passwordHasher, new RequestOriginVerifier(PROPERTIES), new CookieUtils(PROPERTIES, clock),
                Mappers.getMapper(SessionMapper.class), ids, PROPERTIES, clock);
    }

    StaffAccountServiceImpl staffAccountService() {
        return new StaffAccountServiceImpl(
                staffAccountRepository, staffRoomAssignmentRepository, roomRepository, departmentRepository,
                authSessionRepository, auditLogService, securityAuditService, Mappers.getMapper(ProfileMapper.class),
                passwordHasher, temporaryPasswordGenerator, ids, clock);
    }

    StaffAccount activeAccount(String username, String password, Role role) {
        return account(username, password, role, AccountStatus.ACTIVE, false);
    }

    StaffAccount account(String username, String password, Role role, AccountStatus status, boolean mustChange) {
        return save(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(password))
                .fullName("Test " + username)
                .role(role)
                .status(status)
                .passwordChangeRequired(mustChange)
                .createdAt(START)
                .updatedAt(START)
                .build());
    }

    StaffAccount save(StaffAccount account) {
        accounts.put(account.getId(), account);
        return account;
    }

    /**
     * Issues tokens the way a successful login does, without going through the login checks.
     */
    SessionTokens login(StaffAccount account) {
        AuthSession session = AuthSession.start(
                ids.next(), account.getId(), clock.instant(), PROPERTIES.session().absoluteLifetime());
        sessions.put(session.getId(), session);
        return refreshTokenService.issueTokens(account, session, clock.instant());
    }

    AuthSession sessionOf(SessionTokens issued) {
        byte[] digest = TokenUtils.sha256(issued.refreshToken());
        UUID sessionId = tokens.stream()
                .filter(token -> Arrays.equals(token.getTokenHash(), digest))
                .findFirst()
                .map(RefreshToken::getAuthSessionId)
                .orElseThrow();
        return sessions.get(sessionId);
    }

    List<SecurityAuditEventType> auditTypes() {
        return auditEvents.stream().map(SecurityAuditEvent::getEventType).toList();
    }

    private static JwtUtils jwtUtils() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        when(jwtUtils.generateAccessToken(any(), any(), any())).thenAnswer(invocation -> {
            StaffAccount account = invocation.getArgument(0);
            Instant now = invocation.getArgument(2);
            return new IssuedAccessToken(
                    "access:" + account.getId() + ":" + account.getRole() + ":" + account.isPasswordChangeRequired(),
                    now.plus(Duration.ofMinutes(15)));
        });
        return jwtUtils;
    }

    private StaffAccountRepository staffAccountRepository() {
        StaffAccountRepository repository = mock(StaffAccountRepository.class);
        when(repository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(accounts.get(invocation.<UUID>getArgument(0))));
        when(repository.findByUsername(anyString())).thenAnswer(invocation ->
                findByUsername(invocation.getArgument(0)));
        when(repository.findByUsernameForAuthentication(anyString())).thenAnswer(invocation ->
                findByUsername(invocation.getArgument(0)));
        when(repository.existsByRole(any())).thenAnswer(invocation ->
                accounts.values().stream().anyMatch(account -> account.getRole() == invocation.getArgument(0)));
        when(repository.findByRole(any())).thenAnswer(invocation ->
                accounts.values().stream().filter(account -> account.getRole() == invocation.getArgument(0)).findFirst());
        when(repository.existsByEmailIgnoreCaseAndIdNot(anyString(), any())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            UUID excludedId = invocation.getArgument(1);
            return accounts.values().stream().anyMatch(account -> account.getEmail() != null
                    && !account.getId().equals(excludedId)
                    && account.getEmail().equalsIgnoreCase(email));
        });
        when(repository.save(any(StaffAccount.class))).thenAnswer(invocation -> save(invocation.getArgument(0)));
        when(repository.saveAndFlush(any(StaffAccount.class)))
                .thenAnswer(invocation -> save(invocation.getArgument(0)));
        return repository;
    }

    private Optional<StaffAccount> findByUsername(String username) {
        return accounts.values().stream().filter(account -> account.getUsername().equals(username)).findFirst();
    }

    private AuthSessionRepository authSessionRepository() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        when(repository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.<UUID>getArgument(0))));
        when(repository.saveAndFlush(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession session = invocation.getArgument(0);
            sessions.put(session.getId(), session);
            return session;
        });
        when(repository.revoke(any(), any(), any())).thenAnswer(invocation -> {
            AuthSession session = sessions.get(invocation.<UUID>getArgument(0));
            return session != null && session.revoke(invocation.getArgument(1), invocation.getArgument(2)) ? 1 : 0;
        });
        when(repository.revokeAllForAccountExcept(any(), any(), any(), any())).thenAnswer(invocation -> {
            UUID accountId = invocation.getArgument(0);
            UUID keptSessionId = invocation.getArgument(1);
            SessionRevocationReason reason = invocation.getArgument(2);
            Instant now = invocation.getArgument(3);
            return (int) sessions.values().stream()
                    .filter(session -> session.getStaffAccountId().equals(accountId)
                            && !session.getId().equals(keptSessionId))
                    .filter(session -> session.revoke(reason, now))
                    .count();
        });
        when(repository.revokeAllForAccount(any(), any(), any())).thenAnswer(invocation -> {
            UUID accountId = invocation.getArgument(0);
            SessionRevocationReason reason = invocation.getArgument(1);
            Instant now = invocation.getArgument(2);
            return (int) sessions.values().stream()
                    .filter(session -> session.getStaffAccountId().equals(accountId))
                    .filter(session -> session.revoke(reason, now))
                    .count();
        });
        when(repository.deleteEndedBefore(any())).thenAnswer(invocation -> {
            Instant cutoff = invocation.getArgument(0);
            List<UUID> ended = sessions.values().stream()
                    .filter(session -> session.getAbsoluteExpiresAt().isBefore(cutoff)
                            || (session.getRevokedAt() != null && session.getRevokedAt().isBefore(cutoff)))
                    .map(AuthSession::getId)
                    .toList();
            ended.forEach(sessions::remove);
            tokens.removeIf(token -> ended.contains(token.getAuthSessionId()));
            return ended.size();
        });
        return repository;
    }

    private RefreshTokenRepository refreshTokenRepository() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.saveAndFlush(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            tokens.add(token);
            return token;
        });
        when(repository.findByTokenHash(any())).thenAnswer(invocation -> findToken(invocation.getArgument(0)));
        when(repository.markUsedIfUnused(any(), any())).thenAnswer(invocation ->
                findToken(invocation.getArgument(0))
                        .filter(token -> token.markUsed(invocation.getArgument(1)))
                        .isPresent() ? 1 : 0);
        return repository;
    }

    private Optional<RefreshToken> findToken(byte[] tokenHash) {
        return tokens.stream().filter(token -> Arrays.equals(token.getTokenHash(), tokenHash)).findFirst();
    }

    private SecurityAuditEventRepository securityAuditEventRepository() {
        SecurityAuditEventRepository repository = mock(SecurityAuditEventRepository.class);
        when(repository.save(any(SecurityAuditEvent.class))).thenAnswer(invocation -> {
            SecurityAuditEvent event = invocation.getArgument(0);
            auditEvents.add(event);
            return event;
        });
        return repository;
    }

    private AuditLogRepository auditLogRepository() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog auditLog = invocation.getArgument(0);
            businessAuditLogs.add(auditLog);
            return auditLog;
        });
        return repository;
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
