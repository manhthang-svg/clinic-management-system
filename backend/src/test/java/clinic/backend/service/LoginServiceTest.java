package clinic.backend.service;

import java.time.Duration;

import clinic.backend.model.AccountStatus;
import clinic.backend.model.Role;
import clinic.backend.model.StaffAccount;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.service.AuthenticationResult.Authenticated;
import clinic.backend.service.AuthenticationResult.Rejected;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginServiceTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final LoginService loginService = fixture.loginService();

    @Test
    void activeAccountWithCorrectPasswordReceivesASession() {
        StaffAccount doctor = fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR);

        AuthenticationResult result = loginService.login("DR.AN", "correct-password");

        assertThat(result).isInstanceOf(Authenticated.class);
        SessionTokens tokens = ((Authenticated) result).tokens();
        assertThat(tokens.account().id()).isEqualTo(doctor.id());
        assertThat(tokens.account().role()).isEqualTo(Role.DOCTOR);
        assertThat(tokens.passwordChangeRequired()).isFalse();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.sessionExpiresAt()).isEqualTo(AccountTestFixture.START.plus(Duration.ofHours(12)));
        assertThat(fixture.sessions.sessions).hasSize(1);
        assertThat(fixture.sessions.tokens).hasSize(1);
        assertThat(fixture.auditTypes()).containsExactly(EventType.LOGIN_SUCCEEDED);
    }

    @Test
    void unknownUsernameIsRejectedAfterAComparableHashCheckAndIsNotAudited() {
        AuthenticationResult result = loginService.login("nobody", "whatever-password");

        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(fixture.passwordHasher.simulatedMatches).isEqualTo(1);
        assertThat(fixture.auditLog.events).isEmpty();
    }

    @Test
    void wrongPasswordIsRejectedCountedAndAudited() {
        StaffAccount cashier = fixture.activeAccount("cashier", "correct-password", Role.CASHIER);

        AuthenticationResult result = loginService.login("cashier", "wrong-password");

        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(fixture.accounts.byId.get(cashier.id()).failedLoginAttempts()).isEqualTo(1);
        assertThat(fixture.auditTypes()).containsExactly(EventType.LOGIN_FAILED);
        assertThat(fixture.sessions.sessions).isEmpty();
    }

    @Test
    void fiveFailuresBlockLoginEvenWithTheCorrectPasswordUntilTheBlockEnds() {
        fixture.activeAccount("cashier", "correct-password", Role.CASHIER);
        for (int attempt = 0; attempt < 5; attempt++) {
            loginService.login("cashier", "wrong-password");
        }
        assertThat(fixture.auditTypes()).contains(EventType.LOGIN_THROTTLED);

        assertThat(loginService.login("cashier", "correct-password")).isInstanceOf(Rejected.class);

        fixture.clock.advance(Duration.ofMinutes(15));
        assertThat(loginService.login("cashier", "correct-password")).isInstanceOf(Authenticated.class);
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheCorrectPassword() {
        fixture.account("locked.user", "correct-password", Role.RECEPTIONIST, AccountStatus.LOCKED, false);

        AuthenticationResult result = loginService.login("locked.user", "correct-password");

        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(fixture.auditTypes()).containsExactly(EventType.LOGIN_FAILED);
        assertThat(fixture.sessions.sessions).isEmpty();
    }

    @Test
    void accountThatMustChangeItsPasswordLogsInWithThatFlag() {
        fixture.account("new.admin", "temporary-pass", Role.ADMIN, AccountStatus.ACTIVE, true);

        AuthenticationResult result = loginService.login("new.admin", "temporary-pass");

        assertThat(((Authenticated) result).tokens().passwordChangeRequired()).isTrue();
    }
}
