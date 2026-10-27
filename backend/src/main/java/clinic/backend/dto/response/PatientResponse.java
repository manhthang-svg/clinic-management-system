package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String mabenhnhan,
        String cccdSo,
        String hochieuSo,
        String hoten,
        LocalDate ngaysinh,
        short gioitinh,
        String nghenghiep,
        String nghenghiepMa,
        String dantoc,
        String dantocMa,
        String ngoaikieu,
        String ngoaikieuMa,
        String diachi,
        String xaphuong,
        String xaphuongMa,
        String tinhthanh,
        String tinhthanhMa,
        String noilamviec,
        String sodienthoai,
        String yeutorh,
        String nhommau,
        String hotennguoithan,
        String nguoithanQuanhe,
        String diachinguoithan,
        String sodienthoainguoithan,
        Long version
) {}