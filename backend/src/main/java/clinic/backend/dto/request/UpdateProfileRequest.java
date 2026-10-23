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
        @Pattern(regexp = "^(?:03[2-9]|05[2689]|07[06789]|08[1-689]|09[0-46-9])[0-9]{7}$",
                message = "Phone number must be a valid Vietnamese mobile number.")
        String phoneNumber,

        @Schema(description = "Optional email address, unique across staff accounts when provided (case-insensitive).",
                example = "doctor@clinic.example", maxLength = 100)
        @Email(message = "Email must have a valid format.")
        @Size(max = 100, message = "Email must not exceed 100 characters.")
        String email) {

    /** UC-4 accepts only contact fields; fail instead of silently ignoring attempted read-only updates. */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Field is not accepted: " + fieldName);
    }
}
