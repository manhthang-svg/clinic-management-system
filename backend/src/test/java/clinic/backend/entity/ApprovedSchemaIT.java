package clinic.backend.entity;

import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalRecordTypePhamVi;
import clinic.backend.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the tables added from the approved schema (I1-D40): rows written with SQL are read back through
 * the entity mappings, and key constraints reject invalid rows.
 *
 * <p>Each test runs in a transaction that is rolled back, so the shared database stays clean.
 */
@Transactional
class ApprovedSchemaIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void mapsMedicalRecordTypesAndAllowsOneDefault() {
        insertMedicalRecordType("15/BV1", true);

        MedicalRecordType type = entityManager.find(MedicalRecordType.class, "15/BV1");
        assertThat(type.getPhamVi()).isEqualTo(MedicalRecordTypePhamVi.OUTPATIENT);
        assertThat(type.isDefault()).isTrue();
        assertThat(type.getVersion()).isZero();

        assertThatThrownBy(() -> insertMedicalRecordType("16/BV1", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsIcd10Flags() {
        jdbcTemplate.update("""
                insert into icd10 (maicd, tenicd, khong_la_benh_chinh, hieu_luc_tu, is_chronic)
                values ('E11', 'Bệnh đái tháo đường type 2', false, date '2026-01-01', true)
                """);

        Icd10 code = entityManager.find(Icd10.class, "E11");
        assertThat(code.isChronic()).isTrue();
        assertThat(code.isKhongLaBenhChinh()).isFalse();
        assertThat(code.isActive()).isTrue();
        assertThat(code.isNew()).isFalse();
    }

    @Test
    void mapsTheMedicineCatalog() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into medicine_catalog (id, mathuocvattu_bv, tenthuocvattu, donvitinh, status,
                    created_at, updated_at, version)
                values (?, 'T-0001', 'Paracetamol 500mg viên nén', 'Viên', 'ACTIVE', now(), now(), 0)
                """, id);

        MedicineCatalog medicine = entityManager.find(MedicineCatalog.class, id);
        assertThat(medicine.getTenthuocvattu()).isEqualTo("Paracetamol 500mg viên nén");
        assertThat(medicine.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
    }

    @Test
    void allowsOneClinicFacility() {
        UUID id = UUID.randomUUID();
        insertClinicFacility(id, "79001");

        assertThat(entityManager.find(ClinicFacility.class, id).getMaCskcb()).isEqualTo("79001");
        assertThatThrownBy(() -> insertClinicFacility(UUID.randomUUID(), "79002"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertMedicalRecordType(String loaiba, boolean isDefault) {
        jdbcTemplate.update("""
                insert into medical_record_types (loaiba, ten, pham_vi, van_ban, is_default,
                    created_at, updated_at, version)
                values (?, 'Bệnh án ngoại trú', 'OUTPATIENT', '32/2023/TT-BYT', ?, now(), now(), 0)
                """, loaiba, isDefault);
    }

    private void insertClinicFacility(UUID id, String code) {
        jdbcTemplate.update("""
                insert into clinic_facility (id, ma_cskcb, ten_cskcb, diachi, sodienthoai,
                    so_giay_phep_hoat_dong, hinh_thuc_to_chuc, created_at, updated_at, version)
                values (?, ?, 'Phòng khám thử nghiệm', '1 Đường Số 1', '0281234567', 'GP-001',
                    'PHONG_KHAM_DA_KHOA', now(), now(), 0)
                """, id, code);
    }
}
