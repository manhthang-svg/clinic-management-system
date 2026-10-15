package clinic.backend.service;

import java.time.Duration;
import java.util.UUID;

import clinic.backend.model.AccountStatus;
import clinic.backend.model.AuthSession;
import clinic.backend.model.Role;
import clinic.backend.model.SessionRevocationReason;
import clinic.backend.model.StaffAccount;
import clinic.backend.repository.SecurityAuditLog.EventType;
import clinic.backend.service.AuthenticationResult.Authenticated;
import clinic.backend.service.AuthenticationResult.Rejected;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshSessionServiceTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final RefreshSessionService refreshService = fixture.refreshSessionService();

    @Test
    void refreshRotatesTheTokenAndKeepsTheSessionExpiry() {
        SessionTokens login = login();

        AuthenticationResult result = refreshService.refresh(login.refreshToken());

        SessionTokens refreshed = ((Authenticated) result).tokens();
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshed.sessionExpiresAt()).isEqualTo(login.sessionExpiresAt());
        assertThat(fixture.sessions.tokens).hasSize(2);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSession() {
        SessionTokens login = login();
        SessionTokens refreshed = ((Authenticated) refreshService.refresh(login.refreshToken())).tokens();

        assertThat(refreshService.refresh(login.refreshToken())).isInstanceOf(Rejected.class);

        AuthSession session = fixture.sessions.sessions.values().iterator().next();
        assertThat(session.revocationReason()).isEqualTo(SessionRevocationReason.REUSE_DETECTED);
        assertThat(fixture.auditTypes()).contains(EventType.REFRESH_TOKEN_REUSE_DETECTED);
        assertThat(refreshService.refresh(refreshed.refreshToken())).isInstanceOf(Rejected.class);
    }

    @Test
    void tokenUnusedForLongerThanTheIdleTimeoutIsRejected() {
        SessionTokens login = login();

        fixture.clock.advance(Duration.ofHours(2));

        assertThat(refreshService.refresh(login.refreshToken())).isInstanceOf(Rejected.class);
    }

    @Test
    void rotationCannotExtendTheSessionBeyondItsAbsoluteLifetime() {
        SessionTokens current = login();
        for (int hour = 0; hour < 11; hour++) {
            fixture.clock.advance(Duration.ofHours(1));
            current = ((Authenticated) refreshService.refresh(current.refreshToken())).tokens();
        }

        fixture.clock.advance(Duration.ofHours(1));

        assertThat(refreshService.refresh(current.refreshToken())).isInstanceOf(Rejected.class);
    }

    @Test
    void lockedAccountCannotRefresh() {
        SessionTokens login = login();
        StaffAccount account = fixture.accounts.byId.get(login.account().id());
        fixture.accounts.save(StaffAccount.restore(
                account.id(), account.username(), account.passwordHash(), account.fullName(), null, null, null,
                account.role(), AccountStatus.LOCKED, false, 0, null, account.createdAt(), account.updatedAt(),
                account.version()));

        assertThat(refreshService.refresh(login.refreshToken())).isInstanceOf(Rejected.class);
    }

    @Test
    void refreshReReadsTheAccountSoTheNewTokenReflectsTheCurrentRole() {
        SessionTokens login = login();
        StaffAccount account = fixture.accounts.byId.get(login.account().id());
        fixture.accounts.save(StaffAccount.restore(
                account.id(), account.username(), account.passwordHash(), account.fullName(), null, null, null,
                Role.ADMIN, AccountStatus.ACTIVE, false, 0, null, account.createdAt(), account.updatedAt(),
                account.version()));

        SessionTokens refreshed = ((Authenticated) refreshService.refresh(login.refreshToken())).tokens();

        assertThat(refreshed.account().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void unknownOrMissingTokensAreRejected() {
        assertThat(refreshService.refresh(null)).isInstanceOf(Rejected.class);
        assertThat(refreshService.refresh(UUID.randomUUID().toString())).isInstanceOf(Rejected.class);
    }

    private SessionTokens login() {
        fixture.activeAccount("dr.binh", "correct-password", Role.DOCTOR);
        return ((Authenticated) fixture.loginService().login("dr.binh", "correct-password")).tokens();
    }
}
