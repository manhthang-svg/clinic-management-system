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
        return validator.validate(new CreatePatientRequest(name, LocalDate.of(1990, 1, 1), (short) 1, null, null, null,
                null, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null, "0901234567", null, null,
                null, guardian, null, null, null));
    }

    private static boolean invalid(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)
                && "INVALID_FORMAT".equals(v.getMessage()));
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

    @Test
    void checksTheNameOnUpdateToo() {
        UpdatePatientRequest request = new UpdatePatientRequest("Lê Văn 9", LocalDate.of(1990, 1, 1), (short) 1, null,
                null, null, null, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null, "0901234567", null,
                null, null, null, null, null, null, 0L);
        assertThat(invalid(validator.validate(request), "hoten")).isTrue();
    }
}
