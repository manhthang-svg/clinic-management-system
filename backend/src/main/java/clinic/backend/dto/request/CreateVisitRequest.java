
package clinic.backend.dto.request;

import clinic.backend.enums.VisitDoituongbnLoai;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVisitRequest(

        @NotNull(message = "Vui lòng chọn bệnh nhân")
        UUID patientId,

        @NotNull(message = "Vui lòng chọn phòng khám")
        UUID roomId,

        @NotNull(message = "Vui lòng chọn đối tượng bệnh nhân")
        VisitDoituongbnLoai doituongbnLoai,

        @Size(max = 15, message = "Mã BHYT không được vượt quá 15 ký tự")
        String mabhyt,

        @NotBlank(message = "Vui lòng nhập lý do vào viện")
        @Size(max = 500, message = "Lý do vào viện không được vượt quá 500 ký tự")
        String lydovaovien,

        @Size(max = 255, message = "Lý do tiếp nhận không được vượt quá 255 ký tự")
        String lydotiepnhan,

        @Size(max = 200, message = "Nơi giới thiệu không được vượt quá 200 ký tự")
        String noigioithieuLoai,

        Boolean isPriority,

        String priorityReason
) {
}
