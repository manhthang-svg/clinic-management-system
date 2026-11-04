package clinic.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the database invariants introduced for Report 3 UC-22 and UC-23. */
@Transactional
class ServiceCatalogSchemaIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void defaultsNewServicesToInactiveAndGroupsToActive() {
        CatalogFixture fixture = insertCatalogFixture("DEFAULTS");
        UUID serviceId = insertLabService(fixture, "XN-DEFAULT", "Xet nghiem mac dinh", null);

        assertThat(jdbcTemplate.queryForObject(
                "select status from services where id = ?", String.class, serviceId))
                .isEqualTo("INACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "select is_active from service_groups where id = ?", Boolean.class, fixture.groupId()))
                .isTrue();
    }

    @Test
    void rejectsNormalizedDuplicateServiceCodes() {
        CatalogFixture fixture = insertCatalogFixture("UNIQUE");
        insertLabService(fixture, "XN-UNIQUE", "Cong thuc mau", null);

        assertThatThrownBy(() -> insertLabService(
                fixture, " xn-unique ", "Dich vu khac", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsDuplicateServiceNames() {
        CatalogFixture fixture = insertCatalogFixture("NAMES");
        insertLabService(fixture, "XN-NAME-1", "Cong thuc mau", null);

        UUID secondService = insertLabService(fixture, "XN-NAME-2", "Cong thuc mau", null);

        assertThat(secondService).isNotNull();
    }

    @Test
    void rejectsInvalidServiceNameLength() {
        CatalogFixture fixture = insertCatalogFixture("LENGTH");

        assertThatThrownBy(() -> insertLabService(fixture, "XN-SHORT", "X", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidServiceDescriptionLength() {
        CatalogFixture fixture = insertCatalogFixture("DESCRIPTION-LENGTH");

        assertThatThrownBy(() -> insertLabService(
                fixture, "XN-DESCRIPTION", "Mo ta qua dai", "x".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void linksReferenceRangesDirectlyToParameters() {
        CatalogFixture fixture = insertCatalogFixture("REFERENCE");
        UUID serviceId = insertLabService(fixture, "XN-REFERENCE", "Xet nghiem tham chieu", null);
        UUID parameterId = insertLabParameter("PARAM-REFERENCE");
        UUID mappingId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into service_lab_parameters
                    (id, service_id, parameter_id, display_order, created_at, updated_at, version)
                values (?, ?, ?, 1, now(), now(), 0)
                """, mappingId, serviceId, parameterId);

        assertThat(jdbcTemplate.queryForObject(
                "select is_active from service_lab_parameters where id = ?", Boolean.class, mappingId))
                .isTrue();

        UUID referenceRangeId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into lab_reference_ranges
                    (id, parameter_id, reference_type, reference_text, effective_from, source,
                     created_at, updated_at)
                values (?, ?, 'TEXT', 'Am tinh', current_date, 'TEST', now(), now())
                """, referenceRangeId, parameterId);

        assertThat(jdbcTemplate.queryForObject(
                "select parameter_id from lab_reference_ranges where id = ?", UUID.class, referenceRangeId))
                .isEqualTo(parameterId);
        assertThat(jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'lab_reference_ranges'
                  and column_name = 'service_lab_parameter_id'
                """, String.class)).isEmpty();
    }

    @Test
    void referenceRangeRequiresAnExistingParameter() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into lab_reference_ranges
                    (id, parameter_id, reference_type, reference_text, effective_from, source,
                     created_at, updated_at)
                values (?, ?, 'TEXT', 'Am tinh', current_date, 'TEST', now(), now())
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referenceRangeCanBeActivatedWithoutVerificationColumns() {
        UUID parameterId = insertLabParameter("PARAM-DIRECT-ACTIVE");
        UUID rangeId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into lab_reference_ranges
                    (id, parameter_id, reference_type, lower_bound, upper_bound,
                     effective_from, source, is_active, created_at, updated_at)
                values (?, ?, 'RANGE', 1, 2, current_date, 'TEST', true, now(), now())
                """, rangeId, parameterId);

        assertThat(jdbcTemplate.queryForObject(
                "select is_active from lab_reference_ranges where id = ?", Boolean.class, rangeId))
                .isTrue();
        assertThat(jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'lab_reference_ranges'
                  and column_name in ('verified_at', 'verified_by_account_id')
                """, String.class)).isEmpty();
    }

    private CatalogFixture insertCatalogFixture(String suffix) {
        UUID departmentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into departments
                    (id, makhoa, tenkhoa, type, created_at, updated_at, version)
                values (?, ?, ?, 'PARACLINICAL', now(), now(), 0)
                """, departmentId, "K-" + suffix, "Khoa " + suffix);

        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into service_groups
                    (id, loaimachidinh, loaichidinh, type, department_id, created_at, updated_at, version)
                values (?, ?, ?, 'LAB', ?, now(), now(), 0)
                """, groupId, "G-" + suffix, "Nhom " + suffix, departmentId);
        return new CatalogFixture(departmentId, groupId);
    }

    private UUID insertLabService(CatalogFixture fixture, String code, String name, String description) {
        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into services
                    (id, code, tenchidinh, type, department_id, service_group_id,
                     clinic_price, description, created_at, updated_at, version)
                values (?, ?, ?, 'LAB', ?, ?, 100000, ?, now(), now(), 0)
                """, serviceId, code, name, fixture.departmentId(), fixture.groupId(), description);
        return serviceId;
    }

    private UUID insertLabParameter(String code) {
        UUID parameterId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into lab_parameters
                    (id, code, name, value_type, created_at, updated_at, version)
                values (?, ?, ?, 'TEXT', now(), now(), 0)
                """, parameterId, code, "Chi so " + code);
        return parameterId;
    }

    private record CatalogFixture(UUID departmentId, UUID groupId) {
    }
}
