package clinic.backend.service.impl;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import clinic.backend.entity.Icd10;
import clinic.backend.entity.Icd10.CatalogEntry;
import clinic.backend.service.Icd10ImportService.InvalidCatalogException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Icd10CatalogParserTest {

    private static final String HEADER = "﻿ma,ten,ma_cha,ghi_chu\r\n";

    @Test
    void readsCodesFlagsAndFreeTextNotes() {
        List<CatalogEntry> entries = parse(HEADER
                + "A00,Bệnh tả,,c26_co_ma_chi_tiet_hon\r\n"
                + "A00.0,\"Bệnh tả do vi khuẩn Vibrio cholerae 01, típ sinh học cholerae\",A00,\r\n"
                + "B26,Quai bị,,c26_co_ma_chi_tiet_hon\r\n"
                + "B26.0,Viêm tinh hoàn do quai bị (N51.1*),B26,dao_gam|c29_nam\r\n"
                + "N51,Rối loạn cơ quan sinh dục nam,,c26_co_ma_chi_tiet_hon|c29_nam\r\n"
                + "N51.1,Rối loạn tinh hoàn,N51,dau_sao|c24_khong_dung_benh_chinh|c29_nam\r\n"
                + "U13,Mã khẩn cấp,,c26_co_ma_chi_tiet_hon\r\n"
                + "U13.9,Mã khẩn cấp U13.9,U13,\"bản gốc in \"\"U13/9\"\" ở cột 18\"\r\n");

        assertThat(entries).hasSize(8);
        assertThat(entries.get(0)).satisfies(e -> {
            assertThat(e.moreDetailedCodeExists()).isTrue();
            assertThat(e.parentCode()).isNull();
        });
        assertThat(entries.get(1).name()).isEqualTo("Bệnh tả do vi khuẩn Vibrio cholerae 01, típ sinh học cholerae");
        assertThat(entries.get(1).parentCode()).isEqualTo("A00");
        assertThat(entries.get(3)).satisfies(e -> {
            assertThat(e.mark()).isEqualTo(Icd10.DAGGER);
            assertThat(e.sex()).isEqualTo((short) 1);
        });
        assertThat(entries.get(5)).satisfies(e -> {
            assertThat(e.mark()).isEqualTo(Icd10.ASTERISK);
            assertThat(e.notMainDiagnosis()).isTrue();
        });
        // A token that is not a known flag is a note, never silently a rule.
        assertThat(entries.get(7).note()).isEqualTo("bản gốc in \"U13/9\" ở cột 18");
        assertThat(entries.get(7).moreDetailedCodeExists()).isFalse();
    }

    @Test
    void takesTheFourCharacterParentOfAFiveCharacterCode() {
        Set<String> codes = Set.of("B18", "B18.0", "B18.00", "C00", "C00.00");

        assertThat(Icd10CatalogParser.parentOf("B18.00", codes)).isEqualTo("B18.0");
        assertThat(Icd10CatalogParser.parentOf("C00.00", codes)).isEqualTo("C00");
        assertThat(Icd10CatalogParser.parentOf("B18.0", codes)).isEqualTo("B18");
        assertThat(Icd10CatalogParser.parentOf("B18", codes)).isNull();
    }

    @Test
    void reportsEveryProblemWithItsLineAndAcceptsNoPartialFile() {
        assertThatThrownBy(() -> parse(HEADER
                + "A00,Bệnh tả,,\r\n"
                + "A00,Trùng,,\r\n"
                + "a00.1,Mã thường,A00,\r\n"
                + "A01.0,Thiếu cha,A01,\r\n"
                + "A02.0,Cha sai,A03,\r\n"
                + "A02,Hai giới,,c28_nu|c29_nam\r\n"
                + "A04,,,\r\n"
                + "A05,thiếu cột\r\n"))
                .isInstanceOfSatisfying(InvalidCatalogException.class, ex -> assertThat(ex.errors()).containsExactly(
                        "line 3: duplicate code A00",
                        "line 4: invalid code 'a00.1'",
                        "line 5: parent A01 of A01.0 is not in the file",
                        "line 6: ma_cha of A02.0 must be A02, found 'A03'",
                        "line 7: A02 cannot be for female only and male only",
                        "line 8: name of A04 must have 1-255 characters",
                        "line 9: expected 4 columns, found 2"));
    }

    @Test
    void rejectsAnUnexpectedHeader() {
        assertThatThrownBy(() -> parse("code,name\r\nA00,Bệnh tả\r\n"))
                .isInstanceOfSatisfying(InvalidCatalogException.class,
                        ex -> assertThat(ex.errors()).containsExactly("line 1: header must be ma,ten,ma_cha,ghi_chu"));
    }

    private static List<CatalogEntry> parse(String csv) {
        return Icd10CatalogParser.parse(new StringReader(csv));
    }
}
