package clinic.backend.service.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import clinic.backend.service.Icd10ImportService;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService.InvalidListException;
import clinic.backend.service.LongTermPrescriptionDiseaseImportService.Result;
import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loads the real TT 26/2025/TT-BYT Phụ lục VII against the real ICD-10 list. */
class LongTermPrescriptionDiseaseImportIT extends PostgresIntegrationTest {

    private static final LocalDate EFFECTIVE = LocalDate.of(2025, 7, 1);
    private static final String SOURCE = "TT 26/2025/TT-BYT";

    @Autowired
    private LongTermPrescriptionDiseaseImportService importService;

    @Autowired
    private Icd10ImportService icd10ImportService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loadIcd10() throws Exception {
        clear();
        try (Reader reader = resource("/db/reference/icd10.csv")) {
            icd10ImportService.importCatalog(reader, LocalDate.of(2026, 7, 1), "TT 06/2026/TT-BYT");
        }
    }

    /** The container is shared: leave no imported rows behind for other tests. */
    @AfterEach
    void clear() {
        jdbc.update("delete from long_term_prescription_disease_codes");
        jdbc.update("delete from long_term_prescription_diseases");
        jdbc.update("delete from icd10 where van_ban is not null");
    }

    @Test
    void loadsTheAppendixAndAgainChangesNothing() throws Exception {
        Result first = importReal();

        assertThat(first.total()).isEqualTo(252);
        assertThat(first.created()).isEqualTo(252);
        assertThat(first.warnings()).hasSize(3)
                .anySatisfy(warning -> assertThat(warning).contains("D75.2"))
                .anySatisfy(warning -> assertThat(warning).contains("Z69.64"))
                .anySatisfy(warning -> assertThat(warning).contains("Z69.65"));
        assertThat(codesOf("4.13")).contains("E11", "E11.9").doesNotContain("E11.0", "E11.1");
        assertThat(codesOf("6.26")).isEmpty();
        assertThat(count("select count(*) from long_term_prescription_disease_codes")).isEqualTo(first.codes());

        Result second = importReal();

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(252);
        assertThat(second.codes()).isEqualTo(first.codes());
    }

    @Test
    void retiresARowThatLeavesTheListAndUpdatesChangedCodes() {
        importService.importList(csv("1,1.1,Viêm gan vi rút B mạn tính,B18.1,B18.1\r\n"
                + "2,4.1,Suy tuyến giáp,E03,E03\r\n"), EFFECTIVE, SOURCE);

        Result result = importService.importList(
                csv("1,1.1,Viêm gan vi rút B mạn tính,B18,B18\r\n"), LocalDate.of(2026, 1, 1), SOURCE);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.retired()).isEqualTo(1);
        assertThat(codesOf("1.1")).contains("B18.0", "B18.1");
        assertThat(jdbc.queryForObject(
                "select hieu_luc_den from long_term_prescription_diseases where ma_tt = '4.1'", String.class))
                .isEqualTo("2025-12-31");
    }

    @Test
    void writesNothingWhenTheFileIsInvalid() {
        assertThatThrownBy(() -> importService.importList(
                csv("1,1.1,Viêm gan vi rút B mạn tính,B18.1,B18.1\r\n2,x,Sai,E03,E03\r\n"), EFFECTIVE, SOURCE))
                .isInstanceOf(InvalidListException.class);

        assertThat(count("select count(*) from long_term_prescription_diseases")).isZero();
    }

    private Result importReal() throws Exception {
        try (Reader reader = resource("/db/reference/long-term-prescription-diseases.csv")) {
            return importService.importList(reader, EFFECTIVE, SOURCE);
        }
    }

    private List<String> codesOf(String maTt) {
        return jdbc.queryForList("""
                select c.maicd from long_term_prescription_disease_codes c
                join long_term_prescription_diseases d on d.id = c.disease_id
                where d.ma_tt = ?
                """, String.class, maTt);
    }

    private Reader resource(String path) {
        return new InputStreamReader(getClass().getResourceAsStream(path), StandardCharsets.UTF_8);
    }

    private static Reader csv(String rows) {
        return new StringReader("stt,ma_tt,ten_benh,ma_icd_goc,ma_icd\r\n" + rows);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
