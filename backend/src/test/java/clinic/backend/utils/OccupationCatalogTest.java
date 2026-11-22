package clinic.backend.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OccupationCatalogTest {

    @Test
    void mapsListedOccupationsToTheirCodes() {
        assertThat(OccupationCatalog.codeOf("Công nhân")).contains("04");
        assertThat(OccupationCatalog.codeOf("  y tế ")).contains("09");
        assertThat(OccupationCatalog.codeOf("Hành chính,  sự nghiệp")).contains("08");
        assertThat(OccupationCatalog.codeOf("Khác")).contains("99");
    }

    @Test
    void hasNoCodeForAnUnlistedOccupation() {
        assertThat(OccupationCatalog.codeOf("Kỹ sư")).isEmpty();
        assertThat(OccupationCatalog.codeOf(null)).isEmpty();
    }
}
