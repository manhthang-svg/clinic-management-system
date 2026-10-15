package clinic.backend.service.impl;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.dto.request.LoginRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.enums.SecurityAuditEventType;
import clinic.backend.enums.SessionRevocationReason;
import clinic.backend.exceptions.AppException;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.SessionTokens;
import clinic.backend.service.StaffAccountService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffAccountServiceImplTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final StaffAccountServiceImpl service = fixture.staffAccountService();

    @Nested
    class ChangePassword {

        private static final String CURRENT = "current-password";
        private static final String NEW = "brand-new-password";

        @Test
        void changesThePasswordClearsTheFlagAndKeepsOnlyTheCurrentSession() {
            StaffAccount admin = fixture.account("new.admin", CURRENT, Role.ADMIN, AccountStatus.ACTIVE, true);
            SessionTokens otherComputer = fixture.login(admin);
            SessionTokens thisComputer = fixture.login(admin);

            service.changePassword(principal(admin, thisComputer), new ChangePasswordRequest(CURRENT, NEW));

            assertThat(fixture.accounts.get(admin.getId()).isPasswordChangeRequired()).isFalse();
            assertLoginRejected("new.admin", CURRENT);
            assertThat(login("new.admin", NEW)).isTrue();

            assertThat(fixture.refreshTokenService.rotate(otherComputer.refreshToken())).isEmpty();
            SessionTokens refreshed = fixture.refreshTokenService.rotate(thisComputer.refreshToken()).orElseThrow();
            assertThat(refreshed.passwordChangeRequired()).isFalse();

            assertThat(fixture.sessionOf(otherComputer).getRevocationReason())
                    .isEqualTo(SessionRevocationReason.PASSWORD_CHANGED);
            assertThat(fixture.auditTypes()).contains(SecurityAuditEventType.PASSWORD_CHANGED);
        }

        @Test
        void wrongCurrentPasswordChangesNothingAndCountsAsAFailedAttempt() {
            StaffAccount doctor = fixture.activeAccount("dr.an", CURRENT, Role.DOCTOR);
            StaffPrincipal principal = principal(doctor, fixture.login(doctor));

            assertRejectedWith(ErrorCode.CURRENT_PASSWORD_INCORRECT,
                    () -> service.changePassword(principal, new ChangePasswordRequest("wrong-password", NEW)));

            assertThat(fixture.accounts.get(doctor.getId()).getFailedLoginAttempts()).isEqualTo(1);
            assertThat(login("dr.an", CURRENT)).isTrue();
        }

        @Test
        void repeatedWrongCurrentPasswordsBlockFurtherAttempts() {
            StaffAccount doctor = fixture.activeAccount("dr.an", CURRENT, Role.DOCTOR);
            StaffPrincipal principal = principal(doctor, fixture.login(doctor));
            for (int attempt = 0; attempt < 5; attempt++) {
                assertRejectedWith(ErrorCode.CURRENT_PASSWORD_INCORRECT,
                        () -> service.changePassword(principal, new ChangePasswordRequest("wrong-password", NEW)));
            }

            assertRejectedWith(ErrorCode.CURRENT_PASSWORD_INCORRECT,
                    () -> service.changePassword(principal, new ChangePasswordRequest(CURRENT, NEW)));
            assertThat(fixture.auditTypes()).contains(SecurityAuditEventType.LOGIN_THROTTLED);

            fixture.clock.advance(Duration.ofMinutes(15));
            service.changePassword(principal, new ChangePasswordRequest(CURRENT, NEW));
            assertThat(fixture.auditTypes()).contains(SecurityAuditEventType.PASSWORD_CHANGED);
        }

        @Test
        void newPasswordMustSatisfyThePolicyAndDifferFromTheCurrentOne() {
            StaffAccount doctor = fixture.activeAccount("dr.an", CURRENT, Role.DOCTOR);
            StaffPrincipal principal = principal(doctor, fixture.login(doctor));

            assertPolicyViolation("TOO_SHORT",
                    () -> service.changePassword(principal, new ChangePasswordRequest(CURRENT, "short")));
            assertPolicyViolation("TOO_LONG",
                    () -> service.changePassword(principal, new ChangePasswordRequest(CURRENT, "x".repeat(65))));
            assertPolicyViolation("SAME_AS_CURRENT",
                    () -> service.changePassword(principal, new ChangePasswordRequest(CURRENT, CURRENT)));
            assertThat(login("dr.an", CURRENT)).isTrue();
        }

        @Test
        void lockedAccountCannotChangeItsPassword() {
            StaffAccount locked = fixture.account("locked.user", CURRENT, Role.CASHIER, AccountStatus.LOCKED, false);

            assertRejectedWith(ErrorCode.UNAUTHORIZED, () -> service.changePassword(
                    new StaffPrincipal(locked.getId(), UUID.randomUUID()), new ChangePasswordRequest(CURRENT, NEW)));
        }

        private StaffPrincipal principal(StaffAccount account, SessionTokens tokens) {
            return new StaffPrincipal(account.getId(), fixture.sessionOf(tokens).getId());
        }

        private boolean login(String username, String password) {
            return fixture.authService().login(new LoginRequest(username, password), new MockHttpServletResponse())
                    != null;
        }

        private void assertLoginRejected(String username, String password) {
            assertRejectedWith(ErrorCode.INVALID_CREDENTIALS, () -> login(username, password));
        }

        private void assertPolicyViolation(String violation, ThrowingCallable change) {
            assertThatThrownBy(change).isInstanceOfSatisfying(AppException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
                assertThat(exception.getDetails()).isEqualTo(Map.of("newPassword", violation));
            });
        }
    }

    @Nested
    class FirstAdminBootstrap {

        @Test
        void createsTheFirstAdminWhoMustChangeThePassword() {
            StaffAccount admin =
                    service.createFirstAdminIfMissing("Admin", "initial-pass", "Administrator").orElseThrow();

            assertThat(admin.getUsername()).isEqualTo("admin");
            assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(admin.isPasswordChangeRequired()).isTrue();
            assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.ACCOUNT_CREATED);
        }

        @Test
        void doesNothingWhenAnAdminAlreadyExists() {
            fixture.activeAccount("existing.admin", "some-password", Role.ADMIN);

            assertThat(service.createFirstAdminIfMissing("admin", "initial-pass", "Administrator")).isEmpty();
            assertThat(fixture.accounts).hasSize(1);
        }

        @Test
        void refusesAPasswordThatViolatesThePolicy() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> service.createFirstAdminIfMissing("admin", "short", "Administrator"));
        }
    }

    @Nested
    class DemoAccountPassword {

        @Test
        void setsThePasswordOfAccountsThatStillHaveThePlaceholder() {
            StaffAccount seeded = seededAccount("bs.demo");

            assertThat(service.assignDemoAccountPassword("demo-password")).isEqualTo(1);

            StaffAccount updated = fixture.accounts.get(seeded.getId());
            assertThat(fixture.passwordHasher.matches("demo-password", updated.getPasswordHash())).isTrue();
            assertThat(updated.isPasswordChangeRequired()).isFalse();
        }

        @Test
        void keepsPasswordsThatWereAlreadyChanged() {
            StaffAccount changed = fixture.activeAccount("lt.changed", "own-password", Role.RECEPTIONIST);
            String passwordHash = changed.getPasswordHash();

            assertThat(service.assignDemoAccountPassword("demo-password")).isZero();
            assertThat(fixture.accounts.get(changed.getId()).getPasswordHash()).isEqualTo(passwordHash);
        }

        @Test
        void refusesAPasswordThatViolatesThePolicy() {
            seededAccount("bs.demo");

            assertThatIllegalStateException().isThrownBy(() -> service.assignDemoAccountPassword("short"));
        }

        private StaffAccount seededAccount(String username) {
            return fixture.save(StaffAccount.builder()
                    .id(fixture.ids.next())
                    .username(username)
                    .passwordHash(StaffAccountService.UNSET_DEMO_PASSWORD_HASH)
                    .fullName("Demo " + username)
                    .role(Role.DOCTOR)
                    .status(AccountStatus.ACTIVE)
                    .createdAt(AccountTestFixture.START)
                    .updatedAt(AccountTestFixture.START)
                    .build());
        }
    }

    private static void assertRejectedWith(ErrorCode errorCode, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
