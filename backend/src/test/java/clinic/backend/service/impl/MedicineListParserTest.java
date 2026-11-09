package clinic.backend.service.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.service.MedicineCatalogImportService.InvalidListException;
import clinic.backend.service.impl.MedicineListParser.Row;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicineListParserTest {

    private static final String HEADER =
            "ma_thuoc,so_dang_ky,ma_thuoc_bhyt,ten_thuoc,hoat_chat,ham_luong,duong_dung,don_vi_tinh,nhom_kiem_soat,"
                    + "dang_bao_che\r\n";

    @Test
    void readsTheFieldsOfBr79AndIgnoresSourceColumns() {
        List<Row> rows = MedicineListParser.parse(new StringReader(HEADER
                + "T0001,840100205826,,EU-Para 500mg Tablet,Paracetamol,500mg,Uống,Viên,THUONG,Viên nén\r\n"
                + "T0002,890112410925,40.17,Solina   Tablets,Diazepam,5mg,Uống,Viên,HUONG_THAN,Viên nén\r\n"));

        assertThat(rows.get(0).entry().maThuocBhyt()).isNull();
        assertThat(rows.get(0).entry().nhomKiemSoat()).isEqualTo(MedicineControlGroup.THUONG);
        assertThat(rows.get(1).entry().name()).isEqualTo("Solina Tablets");
        assertThat(rows.get(1).entry().maThuocBhyt()).isEqualTo("40.17");
        assertThat(rows.get(1).entry().nhomKiemSoat()).isEqualTo(MedicineControlGroup.HUONG_THAN);
    }

    @Test
    void reportsEveryProblemWithItsLineAndAcceptsNoPartialFile() {
        assertThatThrownBy(() -> MedicineListParser.parse(new StringReader(HEADER
                + "t0001,840100205826,,Thuốc,Paracetamol,500mg,Uống,Viên,THUONG,x\r\n"
                + "T0002,840100205826,,Thuốc A,Ibuprofen,400mg,Uống,Viên,THUONG,x\r\n"
                + "T0003,840100205826,,Thuốc B,Ibuprofen,400mg,Uống,Viên,THUONG,x\r\n"
                + "T0004,893100111426,,Thuốc C,I,400mg,,Viên,KHAC,x\r\n")))
                .isInstanceOfSatisfying(InvalidListException.class, ex -> assertThat(ex.errors()).containsExactly(
                        "line 2: ma_thuoc must have 1-50 upper-case letters, digits or hyphens, found 't0001'",
                        "line 4: so_dang_ky 840100205826 is already on line 3",
                        "line 5: hoat_chat must have 2-500 characters",
                        "line 5: duong_dung must have 1 or more characters",
                        "line 5: nhom_kiem_soat must be one of THUONG, GAY_NGHIEN, HUONG_THAN, TIEN_CHAT, found 'KHAC'"));
    }

    @Test
    void rejectsAnUnexpectedHeader() {
        assertThatThrownBy(() -> MedicineListParser.parse(new StringReader("ma_thuoc,ten_thuoc\r\nT0001,Thuốc\r\n")))
                .isInstanceOf(InvalidListException.class);
    }

    @Test
    void theBundledListIsValid() throws Exception {
        List<Row> rows;
        try (Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/db/reference/medicine-catalog.csv"), StandardCharsets.UTF_8)) {
            rows = MedicineListParser.parse(reader);
        }

        assertThat(rows).hasSize(66);
        assertThat(rows).filteredOn(row -> row.entry().nhomKiemSoat() != MedicineControlGroup.THUONG)
                .extracting(row -> row.entry().hoatchat())
                .containsExactly("Diazepam", "Phenobarbital", "Morphin sulfat", "Ephedrin hydroclorid");
    }
}
