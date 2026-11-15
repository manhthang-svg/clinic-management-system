
package clinic.backend.dto.request;

import clinic.backend.enums.VisitDoituongbnLoai;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateVisitRequest(

        @NotNull(message = "Vui lòng chọn bệnh nhân")
        UUID patientId,

        @NotNull(message = "Vui lòng chọn phòng khám")
        UUID roomId,

        @NotNull(message = "Vui lòng chọn đối tượng bệnh nhân")
        VisitDoituongbnLoai doituongbnLoai,

        @Size(max = 15, message = "Mã BHYT hoặc mã BHXH không được vượt quá 15 ký tự")
        @Pattern(regexp = "^(?:[0-9]{10}|[A-Z]{2}[0-9]{13})$", message = "Mã BHYT hoặc mã BHXH không đúng định dạng")
        String mabhyt,

        @NotBlank(message = "Vui lòng nhập lý do vào viện")
        String lydovaovien,

        @Size(max = 255, message = "Lý do tiếp nhận không được vượt quá 255 ký tự")
        String lydotiepnhan,

        @Size(max = 200, message = "Nơi giới thiệu không được vượt quá 200 ký tự")
        String noigioithieuLoai,

        Boolean isPriority,

        String priorityReason,

        UUID followUpAppointmentId,

        Long followUpAppointmentVersion
) {
}
