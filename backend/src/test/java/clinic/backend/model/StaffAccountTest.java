package clinic.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAccountTest {

    private static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    @Test
    void newAccountsAreActiveAndMustChangeTheirPassword() {
        StaffAccount account = newAccount();

        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.passwordChangeRequired()).isTrue();
        assertThat(account.canAuthenticate()).isTrue();
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
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void successfulLoginResetsTheFailureCount() {
        StaffAccount account = newAccount();
        account.registerFailedLogin(NOW);
        account.registerFailedLogin(NOW);

        account.registerSuccessfulLogin(NOW);

        assertThat(account.failedLoginAttempts()).isZero();
        for (int attempt = 1; attempt < StaffAccount.MAX_CONSECUTIVE_FAILED_LOGINS; attempt++) {
            assertThat(account.registerFailedLogin(NOW)).isFalse();
        }
    }

    @Test
    void lockedAccountsCannotAuthenticate() {
        StaffAccount locked = StaffAccount.restore(
                UUID.randomUUID(), new Username("locked.user"), "{bcrypt}hash", "Locked User", null, null, null,
                Role.CASHIER, AccountStatus.LOCKED, false, 0, null, NOW, NOW, 0L);

        assertThat(locked.canAuthenticate()).isFalse();
    }

    private static StaffAccount newAccount() {
        return StaffAccount.create(
                UUID.randomUUID(), new Username("new.user"), "{bcrypt}hash", "New User", null, null, null,
                Role.RECEPTIONIST, NOW);
    }
}
