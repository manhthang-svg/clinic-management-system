package clinic.backend.service.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import clinic.backend.service.Icd10ImportService;
import clinic.backend.service.Icd10ImportService.InvalidCatalogException;
import clinic.backend.service.Icd10ImportService.Result;
import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loads the real TT 06/2026/TT-BYT list into the migrated schema. */
class Icd10ImportIT extends PostgresIntegrationTest {

    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 7, 1);
    private static final String SOURCE = "TT 06/2026/TT-BYT";

    @Autowired
    private Icd10ImportService importService;

    @Autowired
    private JdbcTemplate jdbc;

    /** The container is shared: leave no imported codes behind for other tests (they insert E11, K29 themselves). */
    @BeforeEach
    @AfterEach
    void clearCatalog() {
        jdbc.update("delete from icd10 where van_ban is not null");
    }

    @Test
    void loadsTheWholePublishedListAndAgainChangesNothing() throws Exception {
        Result first = importReal();

        assertThat(first).isEqualTo(new Result(15_844, 15_844, 0, 0, 0));
        assertThat(count("select count(*) from icd10 where co_ma_chi_tiet_hon")).isEqualTo(2_119);
        assertThat(count("select count(*) from icd10 where gioi_tinh_ap_dung = 2")).isEqualTo(933);
        assertThat(count("select count(*) from icd10 where gioi_tinh_ap_dung = 1")).isEqualTo(144);
        assertThat(count("select count(*) from icd10 where ky_hieu = 'DAU_SAO'")).isEqualTo(807);
        assertThat(count("select count(*) from icd10 where ky_hieu = 'DAU_SAO' and not khong_la_benh_chinh"))
                .isZero();
        Map<String, Object> b1800 = jdbc.queryForMap("select ma_cha, hieu_luc_tu, van_ban from icd10 where maicd = 'B18.00'");
        assertThat(b1800.get("ma_cha")).isEqualTo("B18.0");
        assertThat(b1800.get("van_ban")).isEqualTo(SOURCE);
        assertThat(jdbc.queryForObject("select ghi_chu from icd10 where maicd = 'U13.9'", String.class))
                .contains("U13/9");

        Result second = importReal();

        assertThat(second).isEqualTo(new Result(15_844, 0, 0, 15_844, 0));
    }

    @Test
    void retiresACodeThatLeavesTheListInsteadOfDeletingIt() {
        importService.importCatalog(csv("J00,Viêm mũi họng cấp,,\r\nJ01,Viêm xoang cấp,,\r\n"), EFFECTIVE, SOURCE);

        Result result = importService.importCatalog(
                csv("J00,Viêm mũi họng cấp,,\r\n"), LocalDate.of(2027, 1, 1), "TT 01/2027/TT-BYT");

        assertThat(result).isEqualTo(new Result(1, 0, 1, 0, 1));
        Map<String, Object> j01 = jdbc.queryForMap("select is_active, hieu_luc_den from icd10 where maicd = 'J01'");
        assertThat(j01.get("is_active")).isEqualTo(false);
        assertThat(j01.get("hieu_luc_den").toString()).isEqualTo("2026-12-31");
    }

    @Test
    void writesNothingWhenTheFileIsInvalid() {
        assertThatThrownBy(() -> importService.importCatalog(
                csv("J00,Viêm mũi họng cấp,,\r\nJ00.9,Thiếu cha,J01,\r\n"), EFFECTIVE, SOURCE))
                .isInstanceOf(InvalidCatalogException.class);

        assertThat(count("select count(*) from icd10 where maicd = 'J00'")).isZero();
    }

    private Result importReal() throws Exception {
        try (Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/db/reference/icd10.csv"), StandardCharsets.UTF_8)) {
            return importService.importCatalog(reader, EFFECTIVE, SOURCE);
        }
    }

    private static Reader csv(String rows) {
        return new StringReader("ma,ten,ma_cha,ghi_chu\r\n" + rows);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
