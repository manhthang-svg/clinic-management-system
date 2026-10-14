package clinic.backend.account.application;

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

import clinic.backend.account.application.port.AccessTokenIssuer;
import clinic.backend.account.application.port.AuthSessionRepository;
import clinic.backend.account.application.port.PasswordHasher;
import clinic.backend.account.application.port.SecurityAuditLog;
import clinic.backend.account.application.port.StaffAccountRepository;
import clinic.backend.account.domain.AccountStatus;
import clinic.backend.account.domain.AuthSession;
import clinic.backend.account.domain.RefreshToken;
import clinic.backend.account.domain.Role;
import clinic.backend.account.domain.SessionRevocationReason;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.account.domain.Username;
import clinic.backend.shared.id.UuidV7Generator;

/**
 * In-memory ports and wiring for application-layer tests.
 */
final class AccountTestFixture {

    static final Instant START = Instant.parse("2026-10-14T07:00:00Z");
    static final SessionPolicy SESSION_POLICY =
            new SessionPolicy(Duration.ofHours(2), Duration.ofHours(12), Duration.ofDays(30));

    final MutableClock clock = new MutableClock(START);
    final InMemoryAccounts accounts = new InMemoryAccounts();
    final InMemorySessions sessions = new InMemorySessions();
    final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    final RecordingAuditLog auditLog = new RecordingAuditLog();
    final UuidV7Generator ids = new UuidV7Generator(clock, new SecureRandom());
    final RefreshTokenCodec refreshTokenCodec = new RefreshTokenCodec(new SecureRandom());
    final SessionTokenIssuer sessionTokenIssuer = new SessionTokenIssuer(
            sessions, new FakeAccessTokenIssuer(), refreshTokenCodec, ids, SESSION_POLICY);

    LoginService loginService() {
        return new LoginService(
                accounts, sessions, passwordHasher, sessionTokenIssuer, auditLog, ids, SESSION_POLICY, clock);
    }

    RefreshSessionService refreshSessionService() {
        return new RefreshSessionService(sessions, accounts, refreshTokenCodec, sessionTokenIssuer, auditLog, clock);
    }

    StaffAccount activeAccount(String username, String password, Role role) {
        return account(username, password, role, AccountStatus.ACTIVE, false);
    }

    StaffAccount account(String username, String password, Role role, AccountStatus status, boolean mustChange) {
        return accounts.save(StaffAccount.restore(
                ids.next(), new Username(username), passwordHasher.hash(password), "Test " + username,
                null, null, null, role, status, mustChange, 0, null, START, START, null));
    }

    List<SecurityAuditLog.EventType> auditTypes() {
        return auditLog.events.stream().map(SecurityAuditLog.Event::type).toList();
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

    static final class InMemoryAccounts implements StaffAccountRepository {

        final Map<UUID, StaffAccount> byId = new HashMap<>();

        @Override
        public Optional<StaffAccount> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<StaffAccount> findByUsername(Username username) {
            return byId.values().stream().filter(account -> account.username().equals(username)).findFirst();
        }

        @Override
        public boolean existsWithRole(Role role) {
            return byId.values().stream().anyMatch(account -> account.role() == role);
        }

        @Override
        public StaffAccount save(StaffAccount account) {
            long version = account.version() == null ? 0 : account.version() + 1;
            StaffAccount saved = StaffAccount.restore(
                    account.id(), account.username(), account.passwordHash(), account.fullName(),
                    account.academicTitle(), account.phoneNumber(), account.email(), account.role(),
                    account.status(), account.passwordChangeRequired(), account.failedLoginAttempts(),
                    account.loginBlockedUntil(), account.createdAt(), account.updatedAt(), version);
            byId.put(saved.id(), saved);
            return saved;
        }
    }

    static final class InMemorySessions implements AuthSessionRepository {

        final Map<UUID, AuthSession> sessions = new HashMap<>();
        final List<RefreshToken> tokens = new ArrayList<>();

        @Override
        public void saveSession(AuthSession session) {
            sessions.put(session.id(), session);
        }

        @Override
        public Optional<AuthSession> findSession(UUID sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public void saveToken(RefreshToken token) {
            tokens.add(token);
        }

        @Override
        public Optional<RefreshToken> findTokenByHash(byte[] tokenHash) {
            return tokens.stream().filter(token -> Arrays.equals(token.tokenHash(), tokenHash)).findFirst();
        }

        @Override
        public boolean markTokenUsedIfUnused(byte[] tokenHash, Instant now) {
            for (int index = 0; index < tokens.size(); index++) {
                RefreshToken token = tokens.get(index);
                if (Arrays.equals(token.tokenHash(), tokenHash) && token.usedAt() == null) {
                    tokens.set(index, new RefreshToken(
                            token.id(), token.sessionId(), token.tokenHash(), token.issuedAt(), token.expiresAt(), now));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean revokeSession(UUID sessionId, SessionRevocationReason reason, Instant now) {
            AuthSession session = sessions.get(sessionId);
            if (session == null || session.revokedAt() != null) {
                return false;
            }
            sessions.put(sessionId, new AuthSession(
                    session.id(), session.accountId(), session.createdAt(), session.absoluteExpiresAt(), now, reason));
            return true;
        }

        @Override
        public int deleteSessionsEndedBefore(Instant cutoff) {
            List<UUID> ended = sessions.values().stream()
                    .filter(session -> session.absoluteExpiresAt().isBefore(cutoff)
                            || (session.revokedAt() != null && session.revokedAt().isBefore(cutoff)))
                    .map(AuthSession::id)
                    .toList();
            ended.forEach(sessions::remove);
            tokens.removeIf(token -> ended.contains(token.sessionId()));
            return ended.size();
        }
    }

    static final class FakePasswordHasher implements PasswordHasher {

        int simulatedMatches;

        @Override
        public String hash(String rawPassword) {
            return "{fake}" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals("{fake}" + rawPassword);
        }

        @Override
        public void simulateMatch(String rawPassword) {
            simulatedMatches++;
        }
    }

    static final class FakeAccessTokenIssuer implements AccessTokenIssuer {

        @Override
        public IssuedAccessToken issue(StaffAccount account, Instant now) {
            return new IssuedAccessToken(
                    "access:" + account.id() + ":" + account.role() + ":" + account.passwordChangeRequired(),
                    now.plus(Duration.ofMinutes(15)));
        }
    }

    static final class RecordingAuditLog implements SecurityAuditLog {

        final List<Event> events = new ArrayList<>();

        @Override
        public void record(Event event) {
            events.add(event);
        }
    }
}
