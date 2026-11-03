package clinic.backend.dto.response;

import java.time.Instant;
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
        String email,
        String yeutorh,
        String nhommau,
        String hotennguoithan,
        String nguoithanQuanhe,
        String diachinguoithan,
        String sodienthoainguoithan,
        Long version,
        /** UC-35 results only: registration time of the latest visit, null otherwise. */
        Instant lastVisitAt,
        /** UC-35 results only: the active visit, null when there is none (BR-17). */
        PatientActiveVisitResponse activeVisit
) {}
