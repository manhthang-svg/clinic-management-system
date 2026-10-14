package clinic.backend.account.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordsFromEightToSixtyFourCharacters() {
        assertThat(PasswordPolicy.check("12345678")).isEmpty();
        assertThat(PasswordPolicy.check("a".repeat(64))).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(PasswordPolicy.check("1234567")).contains(PasswordPolicy.Violation.TOO_SHORT);
        assertThat(PasswordPolicy.check(null)).contains(PasswordPolicy.Violation.TOO_SHORT);
    }

    @Test
    void rejectsPasswordsOverSixtyFourCharacters() {
        assertThat(PasswordPolicy.check("a".repeat(65))).contains(PasswordPolicy.Violation.TOO_LONG);
    }

    @Test
    void rejectsPasswordsOverSeventyTwoUtf8Bytes() {
        // 25 Vietnamese letters with diacritics take 3 bytes each in UTF-8: 75 bytes, only 25 characters.
        String password = "ệ".repeat(25);

        assertThat(PasswordPolicy.check(password)).contains(PasswordPolicy.Violation.TOO_LONG);
    }
}
