package clinic.backend.service.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import clinic.backend.service.LongTermPrescriptionDiseaseImportService.InvalidListException;
import clinic.backend.service.impl.LongTermDiseaseListParser.Row;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongTermDiseaseListParserTest {

    private static final String HEADER = "stt,ma_tt,ten_benh,ma_icd_goc,ma_icd\r\n";

    @Test
    void readsCodesRangesAndExclusions() {
        List<Row> rows = LongTermDiseaseListParser.parse(new StringReader(HEADER
                + "1,4.13,Đái tháo đường,\"E10 - E14 (trừ E11.0, E11.1)\",E10..E14 -E11.0 -E11.1\r\n"
                + "2,5.29,Rối loạn do thuốc phiện,F11.2 đến F11.9,F11.2..F11.9\r\n"
                + "3,6.26,Neuroblastomas,,\r\n"));

        Row diabetes = rows.get(0);
        assertThat(diabetes.entry().maIcdGoc()).isEqualTo("E10 - E14 (trừ E11.0, E11.1)");
        assertThat(diabetes.selects("E11")).isTrue();
        assertThat(diabetes.selects("E11.9")).isTrue();
        assertThat(diabetes.selects("E14.9")).isTrue();
        assertThat(diabetes.selects("E11.0")).isFalse();
        assertThat(diabetes.selects("E15")).isFalse();
        assertThat(rows.get(1).selects("F11.5")).isTrue();
        assertThat(rows.get(1).selects("F11.1")).isFalse();
        assertThat(rows.get(1).selects("F11")).isFalse();
        // A row printed without a code selects nothing.
        assertThat(rows.get(2).entry().maIcdGoc()).isNull();
        assertThat(rows.get(2).selects("C74.9")).isFalse();
    }

    @Test
    void reportsEveryProblemWithItsLineAndAcceptsNoPartialFile() {
        assertThatThrownBy(() -> LongTermDiseaseListParser.parse(new StringReader(HEADER
                + "1,4.13,Đái tháo đường,E10 - E14,E14..E10\r\n"
                + "2,x,Bệnh,K25,K25\r\n"
                + "3,1.1,Bệnh,K26,K26\r\n"
                + "3,1.2,Bệnh trùng số,K27,K27\r\n"
                + "5,1.3,Thiếu mã,K28,\r\n"
                + "6,1.4,Mã sai,K29,K2B\r\n")))
                .isInstanceOfSatisfying(InvalidListException.class, ex -> assertThat(ex.errors()).containsExactly(
                        "line 2: invalid range 'E14..E10'",
                        "line 3: invalid ma_tt 'x'",
                        "line 5: duplicate stt 3",
                        "line 6: ma_icd_goc and ma_icd must both be empty or both be given",
                        "line 7: invalid ma_icd item 'K2B'"));
    }

    @Test
    void rejectsAnUnexpectedHeader() {
        assertThatThrownBy(() -> LongTermDiseaseListParser.parse(new StringReader("stt,ten\r\n1,Bệnh\r\n")))
                .isInstanceOf(InvalidListException.class);
    }

    /** The bundled appendix against the bundled ICD-10 list, without a database. */
    @Test
    void theBundledAppendixMatchesTheIcd10ListExceptItsKnownMisprints() throws Exception {
        List<Row> rows;
        try (Reader reader = resource("/db/reference/long-term-prescription-diseases.csv")) {
            rows = LongTermDiseaseListParser.parse(reader);
        }
        Set<String> catalog;
        try (Reader reader = resource("/db/reference/icd10.csv")) {
            catalog = Icd10CatalogParser.readCsv(reader).stream().skip(1).map(row -> row.get(0).strip())
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        assertThat(rows).hasSize(252);
        assertThat(rows).filteredOn(row -> row.entry().maIcdGoc() == null)
                .extracting(row -> row.entry().maTt()).containsExactly("6.26", "10.6");
        List<String> unmatched = rows.stream().flatMap(row -> row.includes().stream())
                .filter(range -> catalog.stream().noneMatch(range::contains))
                .map(Object::toString).toList();
        // Not in TT 06/2026: D75.2 was moved to D47.3; Z69.64 and Z69.65 do not exist.
        assertThat(unmatched).containsExactly("D75.2", "Z69.64", "Z69.65");

        Row diabetes = rows.stream().filter(row -> row.entry().maTt().equals("4.13")).findFirst().orElseThrow();
        Set<String> diabetesCodes = catalog.stream().filter(diabetes::selects).collect(Collectors.toSet());
        assertThat(diabetesCodes).contains("E11", "E11.9", "E14.9").doesNotContain("E10.0", "E11.1", "E14.0");
    }

    private Reader resource(String path) {
        return new InputStreamReader(getClass().getResourceAsStream(path), StandardCharsets.UTF_8);
    }
}
