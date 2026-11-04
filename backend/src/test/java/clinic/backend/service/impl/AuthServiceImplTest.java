package clinic.backend.service.impl;

import java.time.Duration;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.response.SessionResponse;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.SessionTokens;
import jakarta.servlet.http.Cookie;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthServiceImplTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final AuthServiceImpl authService = fixture.authService();

    @Test
    void activeAccountWithCorrectPasswordReceivesASession() {
        StaffAccount doctor = fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SessionResponse session = authService.login(new LoginRequest("DR.AN", "correct-password"), response);

        assertThat(session.account().id()).isEqualTo(doctor.getId());
        assertThat(session.account().role()).isEqualTo("DOCTOR");
        assertThat(session.passwordChangeRequired()).isFalse();
        assertThat(response.getCookie(AccountTestFixture.COOKIE).getValue()).isNotBlank();
        assertThat(response.getCookie(AccountTestFixture.COOKIE).getMaxAge())
                .isEqualTo((int) Duration.ofHours(12).toSeconds());
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(fixture.sessions).hasSize(1);
        assertThat(fixture.tokens).hasSize(1);
        assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.LOGIN_SUCCEEDED);
    }

    @Test
    void unknownUsernameIsRejectedAfterAComparableHashCheckAndIsNotAudited() {
        assertRejected(() -> login("nobody", "whatever-password"));

        verify(fixture.passwordHasher, times(1)).simulateMatch(any());
        assertThat(fixture.auditEvents).isEmpty();
    }

    @Test
    void wrongPasswordIsRejectedCountedAndAudited() {
        StaffAccount cashier = fixture.activeAccount("cashier", "correct-password", Role.CASHIER);

        assertRejected(() -> login("cashier", "wrong-password"));

        assertThat(fixture.accounts.get(cashier.getId()).getFailedLoginAttempts()).isEqualTo(1);
        assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.LOGIN_FAILED);
        assertThat(fixture.sessions).isEmpty();
    }

    @Test
    void fiveFailuresBlockLoginEvenWithTheCorrectPasswordUntilTheBlockEnds() {
        fixture.activeAccount("cashier", "correct-password", Role.CASHIER);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertRejected(() -> login("cashier", "wrong-password"));
        }
        assertTemporarilyBlocked(() -> login("cashier", "wrong-password"));
        assertThat(fixture.auditTypes()).contains(SecurityAuditEventType.LOGIN_THROTTLED);

        assertTemporarilyBlocked(() -> login("cashier", "correct-password"));

        fixture.clock.advance(Duration.ofMinutes(15));
        assertThat(login("cashier", "correct-password").account().username()).isEqualTo("cashier");
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheCorrectPassword() {
        fixture.account("locked.user", "correct-password", Role.RECEPTIONIST, AccountStatus.LOCKED, false);

        assertThatThrownBy(() -> login("locked.user", "correct-password"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));

        assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.LOGIN_FAILED);
        assertThat(fixture.sessions).isEmpty();
    }

    @Test
    void accountThatMustChangeItsPasswordLogsInWithThatFlag() {
        fixture.account("new.admin", "temporary-pass", Role.ADMIN, AccountStatus.ACTIVE, true);

        assertThat(login("new.admin", "temporary-pass").passwordChangeRequired()).isTrue();
    }

    @Test
    void refreshRotatesTheCookie() {
        SessionTokens issued = fixture.login(fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.refresh(cookieRequest(issued.refreshToken(), AccountTestFixture.FRONTEND_ORIGIN), response);

        assertThat(response.getCookie(AccountTestFixture.COOKIE).getValue())
                .isNotBlank()
                .isNotEqualTo(issued.refreshToken());
    }

    @Test
    void rejectedRefreshClearsTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.refresh(
                cookieRequest("unknown-token", AccountTestFixture.FRONTEND_ORIGIN), response))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_INVALID));
        assertThat(response.getCookie(AccountTestFixture.COOKIE).getMaxAge()).isZero();
    }

    @Test
    void cookieEndpointsRequireTheFrontendOrigin() {
        SessionTokens issued = fixture.login(fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR));

        for (String origin : new String[] {null, "https://evil.example"}) {
            assertThatThrownBy(() -> authService.refresh(
                    cookieRequest(issued.refreshToken(), origin), new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(AppException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORIGIN_NOT_ALLOWED));
            assertThatThrownBy(() -> authService.logout(
                    cookieRequest(issued.refreshToken(), origin), new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(AppException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORIGIN_NOT_ALLOWED));
        }
        assertThat(fixture.sessionOf(issued).isUsable(fixture.clock.instant())).isTrue();
    }

    @Test
    void logoutEndsTheSessionAndClearsTheCookie() {
        SessionTokens issued = fixture.login(fixture.activeAccount("dr.an", "correct-password", Role.DOCTOR));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.logout(cookieRequest(issued.refreshToken(), AccountTestFixture.FRONTEND_ORIGIN), response);

        assertThat(response.getCookie(AccountTestFixture.COOKIE).getMaxAge()).isZero();
        assertThat(fixture.sessionOf(issued).isUsable(fixture.clock.instant())).isFalse();
    }

    private SessionResponse login(String username, String password) {
        return authService.login(new LoginRequest(username, password), new MockHttpServletResponse());
    }

    private static void assertRejected(ThrowingCallable login) {
        assertThatThrownBy(login).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    private static void assertTemporarilyBlocked(ThrowingCallable login) {
        assertThatThrownBy(login).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_TEMPORARILY_BLOCKED));
    }

    private static MockHttpServletRequest cookieRequest(String refreshToken, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(new Cookie(AccountTestFixture.COOKIE, refreshToken));
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        return request;
    }
}
