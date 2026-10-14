package clinic.backend.account.application;

import clinic.backend.account.application.port.SecurityAuditLog.EventType;
import clinic.backend.account.domain.Role;
import clinic.backend.account.domain.StaffAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AdminBootstrapServiceTest {

    private final AccountTestFixture fixture = new AccountTestFixture();
    private final AdminBootstrapService service = new AdminBootstrapService(
            fixture.accounts, fixture.passwordHasher, fixture.auditLog, fixture.ids, fixture.clock);

    @Test
    void createsTheFirstAdminWhoMustChangeThePassword() {
        StaffAccount admin = service.createFirstAdminIfMissing("Admin", "initial-pass", "Administrator").orElseThrow();

        assertThat(admin.username().value()).isEqualTo("admin");
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.passwordChangeRequired()).isTrue();
        assertThat(fixture.auditTypes()).containsExactly(EventType.ACCOUNT_CREATED);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        fixture.activeAccount("existing.admin", "some-password", Role.ADMIN);

        assertThat(service.createFirstAdminIfMissing("admin", "initial-pass", "Administrator")).isEmpty();
        assertThat(fixture.accounts.byId).hasSize(1);
    }

    @Test
    void refusesAPasswordThatViolatesThePolicy() {
        assertThatIllegalStateException()
                .isThrownBy(() -> service.createFirstAdminIfMissing("admin", "short", "Administrator"));
    }
}
