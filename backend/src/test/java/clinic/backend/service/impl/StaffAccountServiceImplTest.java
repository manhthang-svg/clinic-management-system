package clinic.backend.service.impl;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.request.UpdateProfileRequest;
import clinic.backend.dto.response.ProfileResponse;
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
    class AdminRecovery {

        @Test
        void resetsPasswordClearsThrottleRevokesSessionsAndAuditsWithoutActor() {
            StaffAccount admin = fixture.activeAccount("admin", "forgotten-password", Role.ADMIN);
            SessionTokens firstSession = fixture.login(admin);
            SessionTokens secondSession = fixture.login(admin);
            for (int attempt = 0; attempt < StaffAccount.MAX_CONSECUTIVE_FAILED_LOGINS; attempt++) {
                admin.registerFailedLogin(AccountTestFixture.START);
            }

            var result = service.recoverAdmin();

            assertThat(result.accountId()).isEqualTo(admin.getId());
            assertThat(result.username()).isEqualTo("admin");
            assertThat(result.temporaryPassword()).hasSize(12);
            assertThat(fixture.passwordHasher.matches(result.temporaryPassword(), admin.getPasswordHash())).isTrue();
            assertThat(admin.isPasswordChangeRequired()).isTrue();
            assertThat(admin.getFailedLoginAttempts()).isZero();
            assertThat(admin.getLoginBlockedUntil()).isNull();
            assertThat(fixture.refreshTokenService.rotate(firstSession.refreshToken())).isEmpty();
            assertThat(fixture.refreshTokenService.rotate(secondSession.refreshToken())).isEmpty();
            assertThat(fixture.sessionOf(firstSession).getRevocationReason())
                    .isEqualTo(SessionRevocationReason.PASSWORD_RESET);
            assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.PASSWORD_RESET);
            assertThat(fixture.auditEvents.getFirst().getActorAccountId()).isNull();
        }

        @Test
        void doesNotUnlockALockedAdmin() {
            StaffAccount admin = fixture.account(
                    "locked.admin", "forgotten-password", Role.ADMIN, AccountStatus.LOCKED, false);

            service.recoverAdmin();

            assertThat(admin.getStatus()).isEqualTo(AccountStatus.LOCKED);
            assertThat(admin.canAuthenticate()).isFalse();
        }

        @Test
        void failsWhenNoAdminExists() {
            assertThatIllegalStateException().isThrownBy(service::recoverAdmin)
                    .withMessage("No Admin account exists to recover");
        }
    }

    @Nested
    class Profile {

        @Test
        void returnsOnlyTheAuthenticatedAccountsProfile() {
            StaffAccount doctor = accountWithContact("dr.profile", "0901234567", "doctor@clinic.test");

            ProfileResponse response = service.getOwnProfile(principal(doctor));

            assertThat(response.username()).isEqualTo("dr.profile");
            assertThat(response.fullName()).isEqualTo("Test dr.profile");
            assertThat(response.phoneNumber()).isEqualTo("0901234567");
            assertThat(response.email()).isEqualTo("doctor@clinic.test");
            assertThat(response.roomName()).isNull();
            assertThat(response.departmentName()).isNull();
        }

        @Test
        void updatesContactNormalizesEmailAndWritesOneAuditEvent() {
            StaffAccount doctor = accountWithContact("dr.update", "0901234567", "old@clinic.test");

            ProfileResponse response = service.updateOwnProfile(
                    principal(doctor), new UpdateProfileRequest("0987654321", "NEW@Clinic.Test"));

            assertThat(response.phoneNumber()).isEqualTo("0987654321");
            assertThat(response.email()).isEqualTo("new@clinic.test");
            assertThat(fixture.auditTypes()).containsExactly(SecurityAuditEventType.ACCOUNT_UPDATED);
            assertThat(fixture.businessAuditLogs).singleElement().satisfies(audit -> {
                assertThat(audit.getActorAccountId()).isEqualTo(doctor.getId());
                assertThat(audit.getEntityId()).isEqualTo(doctor.getId());
                assertThat(audit.getChangedFields()).containsExactly("phoneNumber", "email");
                assertThat(audit.getOldValue()).isEqualTo(
                        "{\"phoneNumber\":\"0901234567\",\"email\":\"old@clinic.test\"}");
                assertThat(audit.getNewValue()).isEqualTo(
                        "{\"phoneNumber\":\"0987654321\",\"email\":\"new@clinic.test\"}");
            });
        }

        @Test
        void duplicateEmailIsRejectedWithoutChangingContact() {
            accountWithContact("existing.user", "0900000000", "used@clinic.test");
            StaffAccount doctor = accountWithContact("dr.duplicate", "0911111111", "old@clinic.test");

            assertRejectedWith(ErrorCode.EMAIL_ALREADY_EXISTS, () -> service.updateOwnProfile(
                    principal(doctor), new UpdateProfileRequest("0922222222", "USED@clinic.test")));

            assertThat(doctor.getPhoneNumber()).isEqualTo("0911111111");
            assertThat(doctor.getEmail()).isEqualTo("old@clinic.test");
            assertThat(fixture.auditTypes()).isEmpty();
            assertThat(fixture.businessAuditLogs).isEmpty();
        }

        @Test
        void unchangedContactIsANoOpWithoutAudit() {
            StaffAccount doctor = accountWithContact("dr.noop", "0901234567", "same@clinic.test");

            service.updateOwnProfile(
                    principal(doctor), new UpdateProfileRequest("0901234567", "SAME@clinic.test"));

            assertThat(fixture.auditTypes()).isEmpty();
            assertThat(fixture.businessAuditLogs).isEmpty();
            assertThat(doctor.getUpdatedAt()).isEqualTo(AccountTestFixture.START);
        }

        @Test
        void lockedAccountCannotReadOrUpdateProfile() {
            StaffAccount locked = fixture.account(
                    "locked.profile", "some-password", Role.CASHIER, AccountStatus.LOCKED, false);

            assertRejectedWith(ErrorCode.UNAUTHORIZED, () -> service.getOwnProfile(principal(locked)));
            assertRejectedWith(ErrorCode.UNAUTHORIZED, () -> service.updateOwnProfile(
                    principal(locked), new UpdateProfileRequest("0901234567", "locked@clinic.test")));
        }

        private StaffAccount accountWithContact(String username, String phone, String email) {
            StaffAccount account = fixture.activeAccount(username, "some-password", Role.DOCTOR);
            account.updateContact(phone, email, AccountTestFixture.START);
            return account;
        }

        private StaffPrincipal principal(StaffAccount account) {
            return new StaffPrincipal(account.getId(), UUID.randomUUID());
        }
    }

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
