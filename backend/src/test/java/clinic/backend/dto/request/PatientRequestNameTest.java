package clinic.backend.dto.request;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-37, UC-38: patient and guardian names accept letters, spaces, ' and - only, like the frontend. */
class PatientRequestNameTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<CreatePatientRequest>> create(String name, String guardian) {
        return validator.validate(new CreatePatientRequest(name, LocalDate.of(1990, 1, 1), (short) 1, null, null,
                "Công nhân", null, "Kinh", null, null, null, "Ha Noi", "Phường Ba Đình", null, "Hà Nội", null, null,
                "0901234567", null, null, null, guardian, null, null, null));
    }

    private static boolean invalid(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)
                && "INVALID_FORMAT".equals(v.getMessage()));
    }

    private static boolean required(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)
                && "REQUIRED".equals(v.getMessage()));
    }

    @Test
    void requiresTheOccupation() {
        Set<ConstraintViolation<CreatePatientRequest>> created = validator.validate(new CreatePatientRequest(
                "Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1, null, null, " ", null, "Kinh", null, null, null,
                "Ha Noi", null, null, "", null, null, "0901234567", null, null, null, null, null, null, null));
        assertThat(required(created, "nghenghiep")).isTrue();

        UpdatePatientRequest update = new UpdatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1,
                null, null, null, null, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null,
                "0901234567", null, null, null, null, null, null, null, 0L);
        assertThat(required(validator.validate(update), "nghenghiep")).isTrue();
    }

    @Test
    void trimsCreateFieldsBeforeValidatingFormatsAndLengths() {
        CreatePatientRequest request = new CreatePatientRequest("  Nguyễn Văn An  ", LocalDate.of(1990, 1, 1),
                (short) 1, " 012345678901 ", " ", "Công nhân", null, "Kinh", null, null, null,
                " " + "a".repeat(1024) + " ", null, null, null, null, null, " 0901234567 ", " an@example.com ",
                null, null, null, null, null, null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.cccdSo()).isEqualTo("012345678901");
        assertThat(request.hochieuSo()).isNull();
    }

    @Test
    void acceptsVietnameseNamesWithApostropheAndHyphen() {
        assertThat(create("Nguyễn Văn Ánh", null)).isEmpty();
        assertThat(create("O'Neil Lê-Hoàng", "Trần Thị B")).isEmpty();
    }

    @Test
    void rejectsDigitsOrSymbolsInTheName() {
        assertThat(invalid(create("Nguyễn Văn 123", null), "hoten")).isTrue();
        assertThat(invalid(create("--", null), "hoten")).isTrue();
        assertThat(invalid(create("Nguyễn Văn A", "Mẹ@1"), "hotennguoithan")).isTrue();
    }

    private Set<ConstraintViolation<CreatePatientRequest>> createWithEmail(String email) {
        return validator.validate(new CreatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1, null,
                null, "Công nhân", null, "Kinh", null, null, null, "Ha Noi", "Phường Ba Đình", null, "Hà Nội", null,
                null, "0901234567", email, null, null, null, null, null, null));
    }

    @Test
    void rejectsAnEmailWhoseDomainHasNoDotLikeTheFrontend() {
        assertThat(invalid(createWithEmail("an@localhost"), "email")).isTrue();
        assertThat(invalid(createWithEmail("an @example.com"), "email")).isTrue();
        assertThat(createWithEmail("an@example.com")).isEmpty();
        assertThat(createWithEmail(" ")).isEmpty();

        UpdatePatientRequest update = new UpdatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1,
                null, null, "Công nhân", null, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null,
                "0901234567", "an@localhost", null, null, null, null, null, null, 0L);
        assertThat(invalid(validator.validate(update), "email")).isTrue();
    }

    @Test
    void acceptsOnlyTenDigitPhoneNumbersForThePatientAndTheGuardian() {
        Set<ConstraintViolation<CreatePatientRequest>> nine = validator.validate(new CreatePatientRequest(
                "Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1, null, null, "Công nhân", null, "Kinh", null, null,
                null, "Ha Noi", "Phường Ba Đình", null, "Hà Nội", null, null, "090123456", null, null, null, null,
                null, null, "09012345ab"));
        assertThat(invalid(nine, "sodienthoai")).isTrue();
        assertThat(invalid(nine, "sodienthoainguoithan")).isTrue();

        UpdatePatientRequest update = new UpdatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1,
                null, null, "Công nhân", null, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null,
                "09012345678", null, null, null, null, null, null, "+84901234567", 0L);
        assertThat(invalid(validator.validate(update), "sodienthoai")).isTrue();
        assertThat(invalid(validator.validate(update), "sodienthoainguoithan")).isTrue();
    }

    @Test
    void checksTheNameOnUpdateToo() {
        UpdatePatientRequest request = new UpdatePatientRequest("Lê Văn 9", LocalDate.of(1990, 1, 1), (short) 1, null,
                null, null, null, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null, "0901234567", null,
                null, null, null, null, null, null, 0L);
        assertThat(invalid(validator.validate(request), "hoten")).isTrue();
    }
}
