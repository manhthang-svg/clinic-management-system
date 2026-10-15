package clinic.backend.service;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.model.StaffAccount;
import clinic.backend.model.Username;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DemoAccountPasswordServiceTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final DemoAccountPasswordService service =
            new DemoAccountPasswordService(fixture.accounts, fixture.passwordHasher, fixture.clock);

    @Test
    void setsThePasswordOfAccountsThatStillHaveThePlaceholder() {
        StaffAccount seeded = seededAccount("bs.demo");

        assertThat(service.assignPassword("demo-password")).isEqualTo(1);

        StaffAccount updated = fixture.accounts.findById(seeded.id()).orElseThrow();
        assertThat(fixture.passwordHasher.matches("demo-password", updated.passwordHash())).isTrue();
        assertThat(updated.passwordChangeRequired()).isFalse();
    }

    @Test
    void keepsPasswordsThatWereAlreadyChanged() {
        StaffAccount changed = fixture.activeAccount("lt.changed", "own-password", Role.RECEPTIONIST);

        assertThat(service.assignPassword("demo-password")).isZero();
        assertThat(fixture.accounts.findById(changed.id()).orElseThrow().passwordHash())
                .isEqualTo(changed.passwordHash());
    }

    @Test
    void refusesAPasswordThatViolatesThePolicy() {
        seededAccount("bs.demo");

        assertThatIllegalStateException().isThrownBy(() -> service.assignPassword("short"));
    }

    private StaffAccount seededAccount(String username) {
        return fixture.accounts.save(StaffAccount.restore(
                fixture.ids.next(), new Username(username), DemoAccountPasswordService.UNSET_PASSWORD_HASH,
                "Demo " + username, null, null, null, Role.DOCTOR, AccountStatus.ACTIVE, false, 0, null,
                AccountTestFixture.START, AccountTestFixture.START, null));
    }
}
