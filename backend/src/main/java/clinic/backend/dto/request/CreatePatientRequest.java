package clinic.backend.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreatePatientRequest(

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 255)
        String hoten,

        @NotNull(message = "Ngày sinh không được để trống")
        @PastOrPresent(message = "Ngày sinh không hợp lệ")
        LocalDate ngaysinh,

        @NotNull(message = "Giới tính không được để trống")
        @Min(value = 1, message = "Giới tính phải từ 1 đến 3")
        @Max(value = 3, message = "Giới tính phải từ 1 đến 3")
        Short gioitinh,

        @Size(max = 12)
        String cccdSo,

        @Size(max = 15)
        String hochieuSo,

        @Size(max = 100)
        String nghenghiep,

        @Size(max = 5)
        String nghenghiepMa,

        @NotBlank(message = "Dân tộc không được để trống")
        @Size(max = 50)
        String dantoc,

        @NotBlank(message = "Mã dân tộc không được để trống")
        @Size(max = 2)
        String dantocMa,

        @Size(max = 255)
        String ngoaikieu,

        @Size(max = 2)
        String ngoaikieuMa,

        @NotBlank(message = "Địa chỉ không được để trống")
        @Size(max = 1024)
        String diachi,

        @Size(max = 1024)
        String xaphuong,

        @Size(max = 5)
        String xaphuongMa,

        @Size(max = 1024)
        String tinhthanh,

        @Size(max = 3)
        String tinhthanhMa,

        @Size(max = 1024)
        String noilamviec,

        @Pattern(
                regexp = "^$|^[0-9]{9,15}$",
                message = "Số điện thoại không hợp lệ"
        )
        @Size(max = 15)
        String sodienthoai,

        @Email
        @Size(max = 100)
        String email,

        @Size(max = 5)
        String yeutorh,

        @Size(max = 5)
        String nhommau,

        @Size(max = 255)
        String hotennguoithan,

        @Size(max = 50)
        String nguoithanQuanhe,

        @Size(max = 1024)
        String diachinguoithan,

        @Pattern(
                regexp = "^$|^[0-9]{9,15}$",
                message = "Số điện thoại người thân không hợp lệ"
        )
        @Size(max = 15)
        String sodienthoainguoithan

) {}
