package clinic.backend.dto.request;

import java.time.LocalDate;

import clinic.backend.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        // BR-59: letters, spaces, apostrophes and hyphens; the 2-120 length applies after normalization.
        @NotBlank @Size(max = 255, message = "TOO_LONG")
        @Pattern(regexp = "^[\\p{L}\\p{M} '-]+$", message = "INVALID_FORMAT")
        String fullName,
        // BR-62: letters, spaces, dots and commas.
        @Size(max = 50, message = "TOO_LONG")
        @Pattern(regexp = "^[\\p{L}\\p{M} .,]*$", message = "INVALID_FORMAT") String academicTitle,
        @Size(min = 5, max = 50, message = "INVALID_FORMAT")
        @Pattern(regexp = "^[\\p{L}\\p{N}/-]+$", message = "INVALID_FORMAT")
        String practiceLicenseNo,
        // BR-49, BR-65: prescriber link code from the national prescription system; Doctor only, optional.
        @Size(max = 50, message = "TOO_LONG")
        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "INVALID_FORMAT")
        String maLienthongBacsi,
        // BR-59-16: personal identification number (12 digits) of a Doctor; optional.
        @Pattern(regexp = "^([0-9]{12})?$", message = "INVALID_FORMAT")
        String citizenId,
        @Past LocalDate dateOfBirth,
        Gender gender,
        @NotBlank @Pattern(regexp = "^(?:03[2-9]|05[2689]|07[06789]|08[1-689]|09[0-46-9])[0-9]{7}$")
        String phoneNumber,
        @Email @Size(max = 100) String email,
        @Size(max = 255) String address,
        @NotNull @PositiveOrZero Long version) {
}
