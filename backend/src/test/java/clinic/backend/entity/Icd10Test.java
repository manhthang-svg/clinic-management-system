package clinic.backend.entity;

import java.time.LocalDate;

import clinic.backend.entity.Icd10.CatalogEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Icd10Test {

    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 7, 1);
    private static final String SOURCE = "TT 06/2026/TT-BYT";

    @Test
    void isUsableOnlyWithinItsEffectiveDatesWhileActive() {
        Icd10 code = Icd10.fromCatalog(entry("J00", "Viêm mũi họng cấp", null), EFFECTIVE, SOURCE);

        assertThat(code.isUsableOn(EFFECTIVE.minusDays(1))).isFalse();
        assertThat(code.isUsableOn(EFFECTIVE)).isTrue();

        code.retire(LocalDate.of(2027, 1, 1));

        assertThat(code.getHieuLucDen()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(code.isUsableOn(LocalDate.of(2026, 12, 31))).isFalse();
        assertThat(code.isActive()).isFalse();
    }

    @Test
    void retiringOnTheSameListDayKeepsTheDatesValid() {
        Icd10 code = Icd10.fromCatalog(entry("J00", "Viêm mũi họng cấp", null), EFFECTIVE, SOURCE);

        assertThat(code.retire(EFFECTIVE)).isTrue();
        assertThat(code.getHieuLucDen()).isEqualTo(EFFECTIVE);
        assertThat(code.retire(EFFECTIVE)).isFalse();
    }

    @Test
    void appliesSexSpecificCodesOnlyToThatSex() {
        Icd10 femaleOnly = Icd10.fromCatalog(entry("A34", "Uốn ván sản khoa", (short) 2), EFFECTIVE, SOURCE);

        assertThat(femaleOnly.appliesToSex((short) 2)).isTrue();
        assertThat(femaleOnly.appliesToSex((short) 1)).isFalse();
        assertThat(femaleOnly.appliesToSex((short) 3)).isTrue();
    }

    @Test
    void reportsOnlyRealChangesAndReactivatesAReturningCode() {
        Icd10 code = Icd10.fromCatalog(entry("J00", "Viêm mũi họng cấp", null), EFFECTIVE, SOURCE);

        assertThat(code.updateFromCatalog(entry("J00", "Viêm mũi họng cấp", null), SOURCE)).isFalse();
        assertThat(code.updateFromCatalog(entry("J00", "Viêm mũi họng cấp tính", null), SOURCE)).isTrue();
        assertThat(code.getTenicd()).isEqualTo("Viêm mũi họng cấp tính");

        code.retire(LocalDate.of(2027, 1, 1));
        assertThat(code.updateFromCatalog(entry("J00", "Viêm mũi họng cấp tính", null), SOURCE)).isTrue();
        assertThat(code.isActive()).isTrue();
        assertThat(code.getHieuLucDen()).isNull();
        assertThat(code.getHieuLucTu()).isEqualTo(EFFECTIVE);
    }

    private static CatalogEntry entry(String code, String name, Short sex) {
        return new CatalogEntry(code, name, null, false, false, false, false, sex, null, null);
    }
}
