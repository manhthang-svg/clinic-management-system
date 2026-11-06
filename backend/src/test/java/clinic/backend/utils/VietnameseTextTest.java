package clinic.backend.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseTextTest {

    @Test
    void removesAccentsCaseAndExtraSpaces() {
        assertThat(VietnameseText.fold("  Viêm   phổi ")).isEqualTo("viem phoi");
        assertThat(VietnameseText.fold("Bệnh đái tháo đường")).isEqualTo("benh dai thao duong");
        assertThat(VietnameseText.fold("ĐAU THẮT NGỰC")).isEqualTo("dau that nguc");
        assertThat(VietnameseText.fold("Nhiễm trùng\nhuyết")).isEqualTo("nhiem trung huyet");
    }

    @Test
    void foldsComposedAndDecomposedInputTheSame() {
        String composed = "Viêm";                          // U+00EA
        String decomposed = "Viêm";                  // e + combining circumflex
        assertThat(VietnameseText.fold(decomposed)).isEqualTo(VietnameseText.fold(composed));
    }

    @Test
    void keepsNullAndCodes() {
        assertThat(VietnameseText.fold(null)).isNull();
        assertThat(VietnameseText.fold("J18.0")).isEqualTo("j18.0");
    }
}
