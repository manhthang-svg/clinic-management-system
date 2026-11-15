package clinic.backend.service.impl;

import java.util.Set;

import clinic.backend.dto.request.UpdateAccountRequest;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BR-59: staff full name has 2–120 letters, spaces, apostrophes or hyphens after normalization. */
class StaffFullNameRuleTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void trimsAndCollapsesRepeatedSpaces() {
        assertThat(AccountAdministrationServiceImpl.normalizeFullName("  Nguyễn   Văn  An  ")).isEqualTo("Nguyễn Văn An");
    }

    @Test
    void acceptsTheTwoAndOneHundredTwentyCharacterBoundaries() {
        assertThat(AccountAdministrationServiceImpl.normalizeFullName("An")).isEqualTo("An");
        String longest = "a".repeat(120);
        assertThat(AccountAdministrationServiceImpl.normalizeFullName(longest)).isEqualTo(longest);
    }

    @Test
    void rejectsANameShorterThanTwoCharacters() {
        assertThatThrownBy(() -> AccountAdministrationServiceImpl.normalizeFullName("  A  "))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsEntry("fullName", "INVALID_FORMAT");
                });
    }

    @Test
    void rejectsANameLongerThanOneHundredTwentyCharacters() {
        assertThatThrownBy(() -> AccountAdministrationServiceImpl.normalizeFullName("a".repeat(121)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetails()).containsEntry("fullName", "TOO_LONG"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Trần-Lê O'Neil", "Nguyễn Thị Hồng"})
    void requestAcceptsLettersSpacesApostrophesAndHyphens(String fullName) {
        assertThat(fullNameViolations(fullName)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Doctor123", "Nguyễn@An"})
    void requestRejectsDigitsAndSymbols(String fullName) {
        assertThat(fullNameViolations(fullName))
                .extracting(ConstraintViolation::getMessage)
                .contains("INVALID_FORMAT");
    }

    private static Set<ConstraintViolation<UpdateAccountRequest>> fullNameViolations(String fullName) {
        UpdateAccountRequest request = new UpdateAccountRequest(
                fullName, null, null, null, null, null, null, "0901234567", null, null, 0L);
        return VALIDATOR.validateProperty(request, "fullName");
    }
}
