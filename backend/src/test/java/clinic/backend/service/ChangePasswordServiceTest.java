package clinic.backend.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import clinic.backend.model.AccountStatus;
import clinic.backend.model.RefreshToken;
import clinic.backend.model.Role;
import clinic.backend.model.SessionRevocationReason;
import clinic.backend.model.StaffAccount;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.service.AuthenticationResult.Authenticated;
import clinic.backend.service.AuthenticationResult.Rejected;
import clinic.backend.service.ChangePasswordResult.AccountUnavailable;
import clinic.backend.service.ChangePasswordResult.Changed;
import clinic.backend.service.ChangePasswordResult.CurrentPasswordIncorrect;
import clinic.backend.service.ChangePasswordResult.PolicyViolated;
import clinic.backend.service.ChangePasswordResult.Violation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePasswordServiceTest {

    private static final String CURRENT = "current-password";
    private static final String NEW = "brand-new-password";

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final ChangePasswordService service = fixture.changePasswordService();

    @Test
    void changesThePasswordClearsTheFlagAndKeepsOnlyTheCurrentSession() {
        StaffAccount admin = fixture.account("new.admin", CURRENT, Role.ADMIN, AccountStatus.ACTIVE, true);
        SessionTokens otherComputer = login("new.admin", CURRENT);
        SessionTokens thisComputer = login("new.admin", CURRENT);

        ChangePasswordResult result = service.changePassword(admin.id(), sessionOf(thisComputer), CURRENT, NEW);

        assertThat(result).isInstanceOf(Changed.class);
        assertThat(fixture.accounts.byId.get(admin.id()).passwordChangeRequired()).isFalse();
        assertThat(fixture.loginService().login("new.admin", CURRENT)).isInstanceOf(Rejected.class);
        assertThat(fixture.loginService().login("new.admin", NEW)).isInstanceOf(Authenticated.class);

        RefreshSessionService refresh = fixture.refreshSessionService();
        assertThat(refresh.refresh(otherComputer.refreshToken())).isInstanceOf(Rejected.class);
        SessionTokens refreshed = ((Authenticated) refresh.refresh(thisComputer.refreshToken())).tokens();
        assertThat(refreshed.passwordChangeRequired()).isFalse();

        assertThat(fixture.sessions.sessions.get(sessionOf(otherComputer)).revocationReason())
                .isEqualTo(SessionRevocationReason.PASSWORD_CHANGED);
        assertThat(fixture.auditTypes()).contains(EventType.PASSWORD_CHANGED);
    }

    @Test
    void wrongCurrentPasswordChangesNothingAndCountsAsAFailedAttempt() {
        StaffAccount doctor = fixture.activeAccount("dr.an", CURRENT, Role.DOCTOR);
        SessionTokens tokens = login("dr.an", CURRENT);

        ChangePasswordResult result = service.changePassword(doctor.id(), sessionOf(tokens), "wrong-password", NEW);

        assertThat(result).isInstanceOf(CurrentPasswordIncorrect.class);
        assertThat(fixture.accounts.byId.get(doctor.id()).failedLoginAttempts()).isEqualTo(1);
        assertThat(fixture.loginService().login("dr.an", CURRENT)).isInstanceOf(Authenticated.class);
    }

    @Test
    void repeatedWrongCurrentPasswordsBlockFurtherAttempts() {
        StaffAccount doctor = fixture.activeAccount("dr.an", CURRENT, Role.DOCTOR);
        UUID session = sessionOf(login("dr.an", CURRENT));
        for (int attempt = 0; attempt < 5; attempt++) {
            service.changePassword(doctor.id(), session, "wrong-password", NEW);
        }

        assertThat(service.changePassword(doctor.id(), session, CURRENT, NEW))
                .isInstanceOf(CurrentPasswordIncorrect.class);
        assertThat(fixture.auditTypes()).contains(EventType.LOGIN_THROTTLED);

        fixture.clock.advance(Duration.ofMinutes(15));
        assertThat(service.changePassword(doctor.id(), session, CURRENT, NEW)).isInstanceOf(Changed.class);
    }

    @Test
    void newPasswordMustSatisfyThePolicyAndDifferFromTheCurrentOne() {
        StaffAccount doctor = fixture.activeAccount("dr.an", CURRENT, Role.DOCTOR);
        UUID session = sessionOf(login("dr.an", CURRENT));

        assertThat(service.changePassword(doctor.id(), session, CURRENT, "short"))
                .isEqualTo(new PolicyViolated(Violation.TOO_SHORT));
        assertThat(service.changePassword(doctor.id(), session, CURRENT, "x".repeat(65)))
                .isEqualTo(new PolicyViolated(Violation.TOO_LONG));
        assertThat(service.changePassword(doctor.id(), session, CURRENT, CURRENT))
                .isEqualTo(new PolicyViolated(Violation.SAME_AS_CURRENT));
        assertThat(fixture.loginService().login("dr.an", CURRENT)).isInstanceOf(Authenticated.class);
    }

    @Test
    void lockedAccountCannotChangeItsPassword() {
        StaffAccount locked = fixture.account("locked.user", CURRENT, Role.CASHIER, AccountStatus.LOCKED, false);

        assertThat(service.changePassword(locked.id(), UUID.randomUUID(), CURRENT, NEW))
                .isInstanceOf(AccountUnavailable.class);
    }

    private SessionTokens login(String username, String password) {
        return ((Authenticated) fixture.loginService().login(username, password)).tokens();
    }

    private UUID sessionOf(SessionTokens tokens) {
        return fixture.sessions.tokens.stream()
                .filter(token -> Arrays.equals(
                        token.tokenHash(), fixture.refreshTokenCodec.digest(tokens.refreshToken())))
                .findFirst()
                .map(RefreshToken::sessionId)
                .orElseThrow();
    }
}
