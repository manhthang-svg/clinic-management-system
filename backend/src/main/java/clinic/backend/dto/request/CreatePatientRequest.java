package clinic.backend.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * UC-37 Create New Patient Profile. Messages are stable codes (REQUIRED, INVALID_FORMAT, TOO_LONG) that the
 * client maps to MSG02, MSG05 and MSG08. Rules that depend on the age are checked by the service.
 */
public record CreatePatientRequest(

        // chi chu cai (co dau), khoang trang, ' va - (giong FE NAME_REGEX)
        @NotBlank(message = "REQUIRED")
        @Size(max = 100, message = "TOO_LONG")
        @Pattern(regexp = "^(?=.*\\p{L})[\\p{L}\\s'-]+$", message = "INVALID_FORMAT")
        String hoten,

        @NotNull(message = "REQUIRED")
        @PastOrPresent(message = "INVALID_FORMAT")
        LocalDate ngaysinh,

        @NotNull(message = "REQUIRED")
        @Min(value = 1, message = "INVALID_FORMAT")
        @Max(value = 3, message = "INVALID_FORMAT")
        Short gioitinh,

        @Pattern(regexp = "^$|^\\d{12}$", message = "INVALID_FORMAT")
        String cccdSo,

        @Pattern(regexp = "^$|^[A-Za-z0-9]{1,15}$", message = "INVALID_FORMAT")
        String hochieuSo,

        @NotBlank(message = "REQUIRED")
        @Size(max = 100, message = "TOO_LONG")
        String nghenghiep,

        @Size(max = 5, message = "TOO_LONG")
        String nghenghiepMa,

        @NotBlank(message = "REQUIRED")
        @Size(max = 50, message = "TOO_LONG")
        String dantoc,

        // optional: derived from dantoc on the server; only used for a name outside the ethnicity list
        @Size(max = 2, message = "TOO_LONG")
        String dantocMa,

        @Size(max = 255, message = "TOO_LONG")
        String ngoaikieu,

        @Size(max = 2, message = "TOO_LONG")
        String ngoaikieuMa,

        @NotBlank(message = "REQUIRED")
        @Size(max = 1024, message = "TOO_LONG")
        String diachi,

        // required unless the occupation is Ngoai kieu (checked by the service)
        @Size(max = 1024, message = "TOO_LONG")
        String xaphuong,

        @Size(max = 5, message = "TOO_LONG")
        String xaphuongMa,

        // required unless the occupation is Ngoai kieu (checked by the service)
        @Size(max = 1024, message = "TOO_LONG")
        String tinhthanh,

        @Size(max = 3, message = "TOO_LONG")
        String tinhthanhMa,

        @Size(max = 1024, message = "TOO_LONG")
        String noilamviec,

        /** Required from 18 years old (checked by the service). */
        @Pattern(regexp = "^$|^\\d{10}$", message = "INVALID_FORMAT")
        String sodienthoai,

        @Email(message = "INVALID_FORMAT")
        @Size(max = 100, message = "TOO_LONG")
        String email,

        @Size(max = 5, message = "TOO_LONG")
        String yeutorh,

        @Size(max = 5, message = "TOO_LONG")
        String nhommau,

        /** Guardian name, required under 18 years old (checked by the service). */
        @Size(max = 255, message = "TOO_LONG")
        @Pattern(regexp = "^$|^(?=.*\\p{L})[\\p{L}\\s'-]+$", message = "INVALID_FORMAT")
        String hotennguoithan,

        /** Guardian relationship, required under 18 years old (checked by the service). */
        @Size(max = 50, message = "TOO_LONG")
        String nguoithanQuanhe,

        @Size(max = 1024, message = "TOO_LONG")
        String diachinguoithan,

        @Pattern(regexp = "^$|^\\d{10}$", message = "INVALID_FORMAT")
        String sodienthoainguoithan

) {
    public CreatePatientRequest {
        hoten = stripToNull(hoten);
        cccdSo = stripToNull(cccdSo);
        hochieuSo = stripToNull(hochieuSo);
        nghenghiep = stripToNull(nghenghiep);
        nghenghiepMa = stripToNull(nghenghiepMa);
        dantoc = stripToNull(dantoc);
        dantocMa = stripToNull(dantocMa);
        ngoaikieu = stripToNull(ngoaikieu);
        ngoaikieuMa = stripToNull(ngoaikieuMa);
        diachi = stripToNull(diachi);
        xaphuong = stripToNull(xaphuong);
        xaphuongMa = stripToNull(xaphuongMa);
        tinhthanh = stripToNull(tinhthanh);
        tinhthanhMa = stripToNull(tinhthanhMa);
        noilamviec = stripToNull(noilamviec);
        sodienthoai = stripToNull(sodienthoai);
        email = stripToNull(email);
        yeutorh = stripToNull(yeutorh);
        nhommau = stripToNull(nhommau);
        hotennguoithan = stripToNull(hotennguoithan);
        nguoithanQuanhe = stripToNull(nguoithanQuanhe);
        diachinguoithan = stripToNull(diachinguoithan);
        sodienthoainguoithan = stripToNull(sodienthoainguoithan);
    }

    private static String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
