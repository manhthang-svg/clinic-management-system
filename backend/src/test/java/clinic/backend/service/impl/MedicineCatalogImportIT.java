package clinic.backend.service.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import clinic.backend.service.MedicineCatalogImportService;
import clinic.backend.service.MedicineCatalogImportService.InvalidListException;
import clinic.backend.service.MedicineCatalogImportService.Result;
import clinic.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loads the bundled medicine list into the migrated schema. */
class MedicineCatalogImportIT extends PostgresIntegrationTest {

    private static final String HEADER =
            "ma_thuoc,so_dang_ky,ma_thuoc_bhyt,ten_thuoc,hoat_chat,ham_luong,duong_dung,don_vi_tinh,nhom_kiem_soat\r\n";

    @Autowired
    private MedicineCatalogImportService importService;

    @Autowired
    private JdbcTemplate jdbc;

    /** The container is shared: leave no medicines behind for other tests. */
    @BeforeEach
    @AfterEach
    void clear() {
        jdbc.update("delete from medicine_catalog");
    }

    @Test
    void loadsTheBundledListAndAgainChangesNothing() throws Exception {
        Result first = importBundled();

        assertThat(first).isEqualTo(new Result(66, 66, 0, 0));
        assertThat(jdbc.queryForObject("select count(*) from medicine_catalog where status = 'ACTIVE'", Long.class))
                .isEqualTo(66);
        assertThat(jdbc.queryForObject(
                "select nhom_kiem_soat from medicine_catalog where hoatchat = 'Diazepam'", String.class))
                .isEqualTo("HUONG_THAN");

        assertThat(importBundled()).isEqualTo(new Result(66, 0, 0, 66));
    }

    @Test
    void updatesAChangedMedicineAndKeepsTheAdminsStatus() {
        importService.importList(csv("T0001,840100205826,,EU-Para,Paracetamol,500mg,Uống,Viên,THUONG\r\n"));
        jdbc.update("update medicine_catalog set status = 'INACTIVE' where mathuocvattu_bv = 'T0001'");

        Result result = importService.importList(
                csv("T0001,840100205826,40.17,EU-Para 500mg,Paracetamol,500mg,Uống,Viên,THUONG\r\n"));

        assertThat(result).isEqualTo(new Result(1, 0, 1, 0));
        assertThat(jdbc.queryForMap(
                "select tenthuocvattu, mathuocvattu_byt, status from medicine_catalog where mathuocvattu_bv = 'T0001'"))
                .containsEntry("tenthuocvattu", "EU-Para 500mg")
                .containsEntry("mathuocvattu_byt", "40.17")
                .containsEntry("status", "INACTIVE");
    }

    @Test
    void rejectsARegistrationNumberOfAnotherMedicineAndWritesNothing() {
        importService.importList(csv("T0001,840100205826,,EU-Para,Paracetamol,500mg,Uống,Viên,THUONG\r\n"));

        assertThatThrownBy(() -> importService.importList(csv(
                "T0002,893100111426,,VACOBUFEN 400,Ibuprofen,400mg,Uống,Viên,THUONG\r\n"
                        + "T0003,840100205826,,Khác,Paracetamol,500mg,Uống,Viên,THUONG\r\n")))
                .isInstanceOfSatisfying(InvalidListException.class, ex -> assertThat(ex.errors())
                        .containsExactly("line 3: so_dang_ky 840100205826 belongs to medicine T0001 in the catalog"));

        assertThat(jdbc.queryForObject("select count(*) from medicine_catalog", Long.class)).isEqualTo(1);
    }

    private Result importBundled() throws Exception {
        try (Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/db/reference/medicine-catalog.csv"), StandardCharsets.UTF_8)) {
            return importService.importList(reader);
        }
    }

    private static Reader csv(String rows) {
        return new StringReader(HEADER + rows);
    }
}
