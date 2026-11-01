package clinic.backend.dto.request;

import clinic.backend.enums.FacilityOrganizationForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * UC-77 body. {@code version} is null when the facility is declared for the first time.
 */
public record UpdateClinicFacilityRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{5}$", message = "The facility code must be exactly 5 digits.")
        String maCskcb,
        @NotBlank @Size(max = 255) String tenCskcb,
        @NotBlank @Size(max = 1024) String diachi,
        // Mobile 0[35789] + 8 digits, landline 02 + 9 digits, or a 1800/1900 hotline.
        @NotBlank @Pattern(
                regexp = "^(?:0[35789][0-9]{8}|02[0-9]{9}|1[89]00[0-9]{4}(?:[0-9]{2})?)$",
                message = "The phone number is not a valid Vietnamese number.")
        String sodienthoai,
        @NotBlank @Size(max = 100) String soGiayPhepHoatDong,
        @NotNull FacilityOrganizationForm hinhThucToChuc,
        @PositiveOrZero Long version) {
}
