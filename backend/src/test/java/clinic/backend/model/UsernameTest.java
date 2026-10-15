package clinic.backend.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UsernameTest {

    @Test
    void parsesCaseInsensitivelyAndTrimsInput() {
        assertThat(Username.parse("  Dr.Nguyen_01 ")).contains(new Username("dr.nguyen_01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "user name", "người-dùng", "user@clinic"})
    void rejectsValuesOutsideTheAllowedFormat(String input) {
        assertThat(Username.parse(input)).isEmpty();
    }

    @Test
    void rejectsTooLongValues() {
        assertThat(Username.parse("a".repeat(51))).isEmpty();
        assertThat(Username.parse("a".repeat(50))).isPresent();
    }

    @Test
    void requiresTheNormalizedFormWhenConstructedDirectly() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Username("Admin"));
    }
}
