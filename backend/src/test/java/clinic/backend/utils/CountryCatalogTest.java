package clinic.backend.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountryCatalogTest {

    @Test
    void mapsListedCountriesToTheirCodes() {
        assertThat(CountryCatalog.codeOf("Anh")).contains("01");
        assertThat(CountryCatalog.codeOf("  hàn   quốc ")).contains("09");
        assertThat(CountryCatalog.codeOf("Cu ba")).contains("05");
        assertThat(CountryCatalog.codeOf("Trung Quốc")).contains("21");
        assertThat(CountryCatalog.codeOf("Khác")).contains("99");
    }

    @Test
    void hasNoCodeForAnUnlistedCountry() {
        assertThat(CountryCatalog.codeOf("Úc")).isEmpty();
        assertThat(CountryCatalog.codeOf(null)).isEmpty();
    }
}
