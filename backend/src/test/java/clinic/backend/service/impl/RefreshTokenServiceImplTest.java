package clinic.backend.service.impl;

import java.time.Duration;
import java.util.UUID;

import clinic.backend.entity.AuthSession;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.service.SessionTokens;
import clinic.backend.utils.TokenUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RefreshTokenServiceImplTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final RefreshTokenServiceImpl refreshTokenService = fixture.refreshTokenService;

    @Test
    void issueStoresOnlyTheDigestOfTheRefreshToken() {
        SessionTokens issued = login();

        assertThat(fixture.tokens).singleElement().satisfies(token ->
                assertThat(token.getTokenHash()).isEqualTo(TokenUtils.sha256(issued.refreshToken())));
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheSessionExpiry() {
        SessionTokens login = login();

        SessionTokens refreshed = refreshTokenService.rotate(login.refreshToken()).orElseThrow();

        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshed.sessionExpiresAt()).isEqualTo(login.sessionExpiresAt());
        assertThat(fixture.tokens).hasSize(2);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSession() {
        SessionTokens login = login();
        SessionTokens refreshed = refreshTokenService.rotate(login.refreshToken()).orElseThrow();

        assertThat(refreshTokenService.rotate(login.refreshToken())).isEmpty();

        AuthSession session = fixture.sessions.values().iterator().next();
        assertThat(session.getRevocationReason()).isEqualTo(SessionRevocationReason.REUSE_DETECTED);
        assertThat(fixture.auditTypes()).contains(SecurityAuditEventType.REFRESH_TOKEN_REUSE_DETECTED);
        assertThat(refreshTokenService.rotate(refreshed.refreshToken())).isEmpty();
    }

    @Test
    void tokenUnusedForLongerThanTheIdleTimeoutIsRejected() {
        SessionTokens login = login();

        fixture.clock.advance(Duration.ofHours(2));

        assertThat(refreshTokenService.rotate(login.refreshToken())).isEmpty();
    }

    @Test
    void rotationCannotExtendTheSessionBeyondItsAbsoluteLifetime() {
        SessionTokens current = login();
        for (int hour = 0; hour < 11; hour++) {
            fixture.clock.advance(Duration.ofHours(1));
            current = refreshTokenService.rotate(current.refreshToken()).orElseThrow();
        }

        fixture.clock.advance(Duration.ofHours(1));

        assertThat(refreshTokenService.rotate(current.refreshToken())).isEmpty();
    }

    @Test
    void lockedAccountCannotRefresh() {
        SessionTokens login = login();
        StaffAccount account = fixture.accounts.get(login.account().id());
        fixture.save(account.toBuilder().status(AccountStatus.LOCKED).build());

        assertThat(refreshTokenService.rotate(login.refreshToken())).isEmpty();
    }

    @Test
    void refreshReReadsTheAccountSoTheNewTokenReflectsTheCurrentRole() {
        SessionTokens login = login();
        StaffAccount account = fixture.accounts.get(login.account().id());
        fixture.save(account.toBuilder().role(Role.ADMIN).build());

        SessionTokens refreshed = refreshTokenService.rotate(login.refreshToken()).orElseThrow();

        assertThat(refreshed.account().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void unknownOrMissingTokensAreRejected() {
        assertThat(refreshTokenService.rotate(null)).isEmpty();
        assertThat(refreshTokenService.rotate(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void logoutEndsTheSessionSoItCanNoLongerBeRefreshed() {
        SessionTokens tokens = login();

        refreshTokenService.revoke(tokens.refreshToken());

        assertThat(refreshTokenService.rotate(tokens.refreshToken())).isEmpty();
        assertThat(fixture.sessionOf(tokens).getRevocationReason()).isEqualTo(SessionRevocationReason.LOGOUT);
        assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.LOGOUT);
    }

    @Test
    void logoutDoesNotEndOtherSessionsOfTheSameAccount() {
        StaffAccount doctor = fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR);
        SessionTokens firstComputer = fixture.login(doctor);
        SessionTokens secondComputer = fixture.login(doctor);

        refreshTokenService.revoke(firstComputer.refreshToken());

        assertThat(refreshTokenService.rotate(secondComputer.refreshToken())).isPresent();
    }

    @Test
    void logoutIsIdempotentAndAuditedOnce() {
        SessionTokens tokens = login();

        refreshTokenService.revoke(tokens.refreshToken());
        refreshTokenService.revoke(tokens.refreshToken());

        assertThat(fixture.auditTypes()).filteredOn(type -> type == SecurityAuditEventType.LOGOUT).hasSize(1);
    }

    @Test
    void logoutIgnoresMissingOrUnknownTokens() {
        assertThatNoException().isThrownBy(() -> {
            refreshTokenService.revoke(null);
            refreshTokenService.revoke("unknown-token");
        });
        assertThat(fixture.auditEvents).isEmpty();
    }

    @Test
    void cleanupDeletesSessionsThatEndedBeforeTheRetentionPeriod() {
        SessionTokens ended = login();
        refreshTokenService.revoke(ended.refreshToken());
        fixture.clock.advance(Duration.ofDays(30).plusSeconds(1));
        SessionTokens active = fixture.login(fixture.accounts.get(ended.account().id()));

        assertThat(refreshTokenService.deleteEndedSessions()).isEqualTo(1);

        assertThat(fixture.sessions).containsOnlyKeys(fixture.sessionOf(active).getId());
        assertThat(fixture.tokens).hasSize(1);
    }

    private SessionTokens login() {
        return fixture.login(fixture.activeAccount("dr.binh", "correct-password", Role.DOCTOR));
    }
}
