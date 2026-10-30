package clinic.backend.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdatePatientRequest(
        @NotBlank @Size(max = 255) String hoten,
        @NotNull @PastOrPresent LocalDate ngaysinh,
        @NotNull @Min(1) @Max(3) Short gioitinh,
        @Size(max = 12) String cccdSo,
        @Size(max = 15) String hochieuSo,
        @Size(max = 100) String nghenghiep,
        @Size(max = 5) String nghenghiepMa,
        @NotBlank @Size(max = 50) String dantoc,
        @NotBlank @Size(max = 2) String dantocMa,
        @Size(max = 255) String ngoaikieu,
        @Size(max = 2) String ngoaikieuMa,
        @NotBlank @Size(max = 1024) String diachi,
        @Size(max = 1024) String xaphuong,
        @Size(max = 5) String xaphuongMa,
        @Size(max = 1024) String tinhthanh,
        @Size(max = 3) String tinhthanhMa,
        @Size(max = 1024) String noilamviec,
        @Pattern(regexp = "^$|^[0-9]{9,15}$") @Size(max = 15) String sodienthoai,
        @Email @Size(max = 100) String email,
        @Size(max = 5) String yeutorh,
        @Size(max = 5) String nhommau,
        @Size(max = 255) String hotennguoithan,
        @Size(max = 50) String nguoithanQuanhe,
        @Size(max = 1024) String diachinguoithan,
        @Pattern(regexp = "^$|^[0-9]{9,15}$") @Size(max = 15) String sodienthoainguoithan,
        @NotNull @PositiveOrZero Long version
) {
}
