package clinic.backend.dto.request;

import java.util.Set;

import clinic.backend.enums.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** BR-62: an academic title has letters, spaces, dots and commas, at most 50 characters. */
class AcademicTitleRuleTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"ThS.BS", "BSCKI", "PGS.TS, BS", "Cử nhân"})
    void acceptsLettersSpacesDotsAndCommas(String title) {
        assertThat(updateViolations(title)).isEmpty();
        assertThat(roleChangeViolations(title)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BS@1", "BSCK2", "ThS/BS"})
    void rejectsDigitsAndOtherSymbols(String title) {
        assertThat(updateViolations(title)).extracting(ConstraintViolation::getMessage).containsExactly("INVALID_FORMAT");
        assertThat(roleChangeViolations(title)).extracting(ConstraintViolation::getMessage).containsExactly("INVALID_FORMAT");
    }

    @ParameterizedTest
    @ValueSource(ints = {51})
    void rejectsMoreThanFiftyCharacters(int length) {
        assertThat(updateViolations("a".repeat(length))).extracting(ConstraintViolation::getMessage).containsExactly("TOO_LONG");
    }

    private static Set<ConstraintViolation<UpdateAccountRequest>> updateViolations(String title) {
        return VALIDATOR.validateProperty(
                new UpdateAccountRequest("Nguyễn Văn An", title, null, null, null, "0901234567", null, null, 0L),
                "academicTitle");
    }

    private static Set<ConstraintViolation<RoleChangeRequest>> roleChangeViolations(String title) {
        return VALIDATOR.validateProperty(
                new RoleChangeRequest(Role.DOCTOR, Role.RECEPTIONIST, title, "000123/HCM-CCHN"), "academicTitle");
    }
}
