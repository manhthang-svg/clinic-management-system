package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.PasswordViolation;
import clinic.backend.enums.Role;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StaffAccountTest {

    private static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    @Test
    void newAccountsAreActiveAndMustChangeTheirPassword() {
        StaffAccount account = newAccount();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isPasswordChangeRequired()).isTrue();
        assertThat(account.canAuthenticate()).isTrue();
    }

    @Test
    void newAccountsKeepTheirProfileAndCreator() {
        UUID creatorId = UUID.randomUUID();
        LocalDate dateOfBirth = LocalDate.of(1990, 5, 20);

        StaffAccount account = StaffAccount.create(
                UUID.randomUUID(), "profiled.user", "{bcrypt}hash", "Profiled User", "BSCKI",
                dateOfBirth, Gender.FEMALE, "0912345678", "profiled@example.com", "1 Nguyen Trai",
                Role.DOCTOR, creatorId, NOW);

        assertThat(account.getDateOfBirth()).isEqualTo(dateOfBirth);
        assertThat(account.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(account.getAddress()).isEqualTo("1 Nguyen Trai");
        assertThat(account.getCreatedByAccountId()).isEqualTo(creatorId);
    }

    @Test
    void fifthConsecutiveFailureBlocksLoginForFifteenMinutes() {
        StaffAccount account = newAccount();

        for (int attempt = 1; attempt < StaffAccount.MAX_CONSECUTIVE_FAILED_LOGINS; attempt++) {
            assertThat(account.registerFailedLogin(NOW)).isFalse();
        }
        assertThat(account.registerFailedLogin(NOW)).isTrue();

        assertThat(account.isLoginBlocked(NOW.plusSeconds(14 * 60 + 59))).isTrue();
        assertThat(account.isLoginBlocked(NOW.plusSeconds(15 * 60))).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void successfulLoginResetsTheFailureCount() {
        StaffAccount account = newAccount();
        account.registerFailedLogin(NOW);
        account.registerFailedLogin(NOW);

        Instant loginTime = NOW.plusSeconds(30);
        account.registerSuccessfulLogin(loginTime);

        assertThat(account.getFailedLoginAttempts()).isZero();
        assertThat(account.getLastLoginAt()).isEqualTo(loginTime);
        for (int attempt = 1; attempt < StaffAccount.MAX_CONSECUTIVE_FAILED_LOGINS; attempt++) {
            assertThat(account.registerFailedLogin(NOW)).isFalse();
        }
    }

    @Test
    void changingPasswordRecordsTheChangeWithoutChangingLastLogin() {
        StaffAccount account = newAccount();
        Instant changedAt = NOW.plusSeconds(60);

        account.changePassword("{bcrypt}new-hash", changedAt);

        assertThat(account.getPasswordChangedAt()).isEqualTo(changedAt);
        assertThat(account.getLastLoginAt()).isNull();
    }

    @Test
    void lockedAccountsCannotAuthenticate() {
        StaffAccount locked = newAccount().toBuilder().status(AccountStatus.LOCKED).build();

        assertThat(locked.canAuthenticate()).isFalse();
    }

    @Test
    void creationRequiresTheNormalizedUsername() {
        assertThatIllegalArgumentException().isThrownBy(() -> StaffAccount.create(
                UUID.randomUUID(), "Admin", "{bcrypt}hash", "Admin", null, null, null, Role.ADMIN, NOW));
    }

    private static StaffAccount newAccount() {
        return StaffAccount.create(
                UUID.randomUUID(), "new.user", "{bcrypt}hash", "New User", null, null, null,
                Role.RECEPTIONIST, NOW);
    }

    @Nested
    class Username {

        @Test
        void parsesCaseInsensitivelyAndTrimsInput() {
            assertThat(StaffAccount.normalizeUsername("  Dr.Nguyen_01 ")).contains("dr.nguyen_01");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "user name", "người-dùng", "user@clinic"})
        void rejectsValuesOutsideTheAllowedFormat(String input) {
            assertThat(StaffAccount.normalizeUsername(input)).isEmpty();
        }

        @Test
        void rejectsTooLongValues() {
            assertThat(StaffAccount.normalizeUsername("a".repeat(51))).isEmpty();
            assertThat(StaffAccount.normalizeUsername("a".repeat(50))).isPresent();
        }
    }

    @Nested
    class PasswordPolicy {

        @Test
        void acceptsPasswordsFromEightToSixtyFourCharacters() {
            assertThat(StaffAccount.checkPasswordPolicy("12345678")).isEmpty();
            assertThat(StaffAccount.checkPasswordPolicy("a".repeat(64))).isEmpty();
        }

        @Test
        void rejectsShortPasswords() {
            assertThat(StaffAccount.checkPasswordPolicy("1234567")).contains(PasswordViolation.TOO_SHORT);
            assertThat(StaffAccount.checkPasswordPolicy(null)).contains(PasswordViolation.TOO_SHORT);
        }

        @Test
        void rejectsPasswordsOverSixtyFourCharacters() {
            assertThat(StaffAccount.checkPasswordPolicy("a".repeat(65))).contains(PasswordViolation.TOO_LONG);
        }

        @Test
        void rejectsPasswordsOverSeventyTwoUtf8Bytes() {
            // 25 Vietnamese letters with diacritics take 3 bytes each in UTF-8: 75 bytes, only 25 characters.
            String password = "ệ".repeat(25);

            assertThat(StaffAccount.checkPasswordPolicy(password)).contains(PasswordViolation.TOO_LONG);
        }
    }
}
