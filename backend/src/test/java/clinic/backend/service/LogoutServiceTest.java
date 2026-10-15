package clinic.backend.service;

import clinic.backend.enums.Role;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.service.AuthenticationResult.Authenticated;
import clinic.backend.service.AuthenticationResult.Rejected;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class LogoutServiceTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final LogoutService logoutService =
            new LogoutService(fixture.sessions, fixture.refreshTokenCodec, fixture.auditLog, fixture.clock);

    @Test
    void logoutEndsTheSessionSoItCanNoLongerBeRefreshed() {
        fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR);
        SessionTokens tokens = login("dr.an");

        logoutService.logout(tokens.refreshToken());

        assertThat(fixture.refreshSessionService().refresh(tokens.refreshToken())).isInstanceOf(Rejected.class);
        assertThat(fixture.auditTypes()).containsSubsequence(EventType.LOGIN_SUCCEEDED, EventType.LOGOUT);
    }

    @Test
    void logoutDoesNotEndOtherSessionsOfTheSameAccount() {
        fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR);
        SessionTokens firstComputer = login("dr.an");
        SessionTokens secondComputer = login("dr.an");

        logoutService.logout(firstComputer.refreshToken());

        assertThat(fixture.refreshSessionService().refresh(secondComputer.refreshToken()))
                .isInstanceOf(Authenticated.class);
    }

    @Test
    void logoutIsIdempotentAndAuditedOnce() {
        fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR);
        SessionTokens tokens = login("dr.an");

        logoutService.logout(tokens.refreshToken());
        logoutService.logout(tokens.refreshToken());

        assertThat(fixture.auditTypes()).filteredOn(type -> type == EventType.LOGOUT).hasSize(1);
    }

    @Test
    void missingOrUnknownTokensAreIgnored() {
        assertThatNoException().isThrownBy(() -> {
            logoutService.logout(null);
            logoutService.logout("unknown-token");
        });
        assertThat(fixture.auditLog.events).isEmpty();
    }

    private SessionTokens login(String username) {
        return ((Authenticated) fixture.loginService().login(username, "correct-password")).tokens();
    }
}
