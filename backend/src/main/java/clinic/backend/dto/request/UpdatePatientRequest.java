package clinic.backend.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * UC-38 Update Patient Information: the UC-37 fields and rules plus the version read with the profile. Messages
 * are stable codes (REQUIRED, INVALID_FORMAT, TOO_LONG); rules that depend on the age are checked by the service.
 */
public record UpdatePatientRequest(
        @NotBlank(message = "REQUIRED") @Size(max = 100, message = "TOO_LONG")
        @Pattern(regexp = "^(?=.*\\p{L})[\\p{L}\\s'-]+$", message = "INVALID_FORMAT") String hoten,
        @NotNull(message = "REQUIRED") @PastOrPresent(message = "INVALID_FORMAT") LocalDate ngaysinh,
        @NotNull(message = "REQUIRED") @Min(value = 1, message = "INVALID_FORMAT")
        @Max(value = 3, message = "INVALID_FORMAT") Short gioitinh,
        @Pattern(regexp = "^$|^\\d{12}$", message = "INVALID_FORMAT") String cccdSo,
        @Pattern(regexp = "^$|^[A-Za-z0-9]{1,15}$", message = "INVALID_FORMAT") String hochieuSo,
        @Size(max = 100, message = "TOO_LONG") String nghenghiep,
        @Size(max = 5, message = "TOO_LONG") String nghenghiepMa,
        @NotBlank(message = "REQUIRED") @Size(max = 50, message = "TOO_LONG") String dantoc,
        // optional: derived from dantoc on the server; only used for a name outside the ethnicity list
        @Size(max = 2, message = "TOO_LONG") String dantocMa,
        @Size(max = 255, message = "TOO_LONG") String ngoaikieu,
        @Size(max = 2, message = "TOO_LONG") String ngoaikieuMa,
        @NotBlank(message = "REQUIRED") @Size(max = 1024, message = "TOO_LONG") String diachi,
        @Size(max = 1024, message = "TOO_LONG") String xaphuong,
        @Size(max = 5, message = "TOO_LONG") String xaphuongMa,
        @Size(max = 1024, message = "TOO_LONG") String tinhthanh,
        @Size(max = 3, message = "TOO_LONG") String tinhthanhMa,
        @Size(max = 1024, message = "TOO_LONG") String noilamviec,
        @Pattern(regexp = "^$|^\\d{10}$", message = "INVALID_FORMAT") String sodienthoai,
        @Email(message = "INVALID_FORMAT") @Size(max = 100, message = "TOO_LONG") String email,
        @Size(max = 5, message = "TOO_LONG") String yeutorh,
        @Size(max = 5, message = "TOO_LONG") String nhommau,
        @Size(max = 255, message = "TOO_LONG")
        @Pattern(regexp = "^$|^(?=.*\\p{L})[\\p{L}\\s'-]+$", message = "INVALID_FORMAT") String hotennguoithan,
        @Size(max = 50, message = "TOO_LONG") String nguoithanQuanhe,
        @Size(max = 1024, message = "TOO_LONG") String diachinguoithan,
        @Pattern(regexp = "^$|^\\d{10}$", message = "INVALID_FORMAT") String sodienthoainguoithan,
        @NotNull(message = "REQUIRED") @PositiveOrZero(message = "INVALID_FORMAT") Long version
) {
}
