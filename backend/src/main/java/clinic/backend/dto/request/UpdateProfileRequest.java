package clinic.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** The only fields a staff member may change on their own profile (UC-4 / BR-04). */
public record UpdateProfileRequest(
        @Schema(description = "Required Vietnamese phone number: exactly 10 digits beginning with 0.",
                example = "0901234567", pattern = "^0[0-9]{9}$")
        @NotBlank(message = "Phone number is required.")
        @Pattern(regexp = "^0[0-9]{9}$", message = "Phone number must contain 10 digits and start with 0.")
        String phoneNumber,

        @Schema(description = "Required email address, unique across staff accounts (case-insensitive).",
                example = "doctor@clinic.example", maxLength = 100)
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must have a valid format.")
        @Size(max = 100, message = "Email must not exceed 100 characters.")
        String email) {

    /** UC-4 accepts only contact fields; fail instead of silently ignoring attempted read-only updates. */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Field is not accepted: " + fieldName);
    }
}
