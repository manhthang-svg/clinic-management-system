package clinic.backend.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EthnicityCatalogTest {

    @Test
    void mapsListedNamesToTheirCodesIgnoringCaseAndSpaces() {
        assertThat(EthnicityCatalog.codeOf("Kinh")).contains("01");
        assertThat(EthnicityCatalog.codeOf("  khmer ")).contains("05");
        assertThat(EthnicityCatalog.codeOf("bru-vân   kiều")).contains("23");
        assertThat(EthnicityCatalog.codeOf("Khơ-me")).contains("05");
        assertThat(EthnicityCatalog.codeOf("HMông")).contains("08");
        assertThat(EthnicityCatalog.codeOf("Không rõ")).contains("56");
        assertThat(EthnicityCatalog.codeOf("Người nước ngoài")).contains("55");
    }

    @Test
    void hasNoCodeForAnUnlistedName() {
        assertThat(EthnicityCatalog.codeOf("Khác")).isEmpty();
        assertThat(EthnicityCatalog.codeOf(null)).isEmpty();
    }
}
