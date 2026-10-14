package clinic.backend.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import clinic.backend.account.application.AuthenticationResult.Authenticated;
import clinic.backend.account.application.AuthenticationResult.Rejected;
import clinic.backend.account.application.port.AuthSessionRepository;
import clinic.backend.account.application.port.PasswordHasher;
import clinic.backend.account.application.port.SecurityAuditLog;
import clinic.backend.account.application.port.SecurityAuditLog.EventType;
import clinic.backend.account.application.port.SecurityAuditLog.Outcome;
import clinic.backend.account.application.port.StaffAccountRepository;
import clinic.backend.account.domain.AuthSession;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.account.domain.Username;
import clinic.backend.shared.id.UuidV7Generator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-1 Login (docs/use-cases/UC-1-login.md).
 */
@Service
public class LoginService {

    private final StaffAccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SessionTokenIssuer sessionTokenIssuer;
    private final SecurityAuditLog auditLog;
    private final UuidV7Generator ids;
    private final SessionPolicy sessionPolicy;
    private final Clock clock;

    public LoginService(
            StaffAccountRepository accounts,
            AuthSessionRepository sessions,
            PasswordHasher passwordHasher,
            SessionTokenIssuer sessionTokenIssuer,
            SecurityAuditLog auditLog,
            UuidV7Generator ids,
            SessionPolicy sessionPolicy,
            Clock clock) {

        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.sessionTokenIssuer = sessionTokenIssuer;
        this.auditLog = auditLog;
        this.ids = ids;
        this.sessionPolicy = sessionPolicy;
        this.clock = clock;
    }

    @Transactional
    public AuthenticationResult login(String username, String password) {
        Instant now = clock.instant();

        Optional<StaffAccount> candidate = Username.parse(username)
                .flatMap(accounts::findByUsernameForAuthentication);
        if (candidate.isEmpty()) {
            passwordHasher.simulateMatch(password);
            return new Rejected();
        }

        StaffAccount account = candidate.get();
        if (account.isLoginBlocked(now)) {
            passwordHasher.simulateMatch(password);
            audit(EventType.LOGIN_FAILED, account, Outcome.FAILURE, now);
            return new Rejected();
        }

        if (!passwordHasher.matches(password, account.passwordHash())) {
            boolean blockStarted = account.registerFailedLogin(now);
            accounts.save(account);
            audit(EventType.LOGIN_FAILED, account, Outcome.FAILURE, now);
            if (blockStarted) {
                audit(EventType.LOGIN_THROTTLED, account, Outcome.FAILURE, now);
            }
            return new Rejected();
        }

        if (!account.canAuthenticate()) {
            audit(EventType.LOGIN_FAILED, account, Outcome.FAILURE, now);
            return new Rejected();
        }

        account.registerSuccessfulLogin(now);
        StaffAccount saved = accounts.save(account);

        AuthSession session = AuthSession.start(ids.next(), saved.id(), now, sessionPolicy.absoluteLifetime());
        sessions.saveSession(session);
        SessionTokens tokens = sessionTokenIssuer.issue(saved, session, now);

        auditLog.record(new SecurityAuditLog.Event(
                EventType.LOGIN_SUCCEEDED, saved.id(), saved.id(), Outcome.SUCCESS, now));
        return new Authenticated(tokens);
    }

    private void audit(EventType type, StaffAccount account, Outcome outcome, Instant now) {
        auditLog.record(new SecurityAuditLog.Event(type, null, account.id(), outcome, now));
    }
}
