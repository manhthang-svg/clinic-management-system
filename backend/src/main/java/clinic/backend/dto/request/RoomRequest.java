package clinic.backend.dto.request;

import java.util.UUID;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomRequest {

    @Size(max = 255, message = "Mã phòng tối đa 255 ký tự")
    private String roomCode;
    private String maphongkham;

    @Size(max = 255, message = "Tên phòng tối đa 255 ký tự")
    private String roomName;
    private String phongkham;

    @NotNull(message = "Khoa trực thuộc không được để trống")
    private UUID departmentId;

    private UUID serviceGroupId;

    @Size(max = 255, message = "Vị trí tối đa 255 ký tự")
    private String location;

    @NotNull(message = "Loại phòng không được để trống")
    private RoomType type;

    private String headPerson;

    @Builder.Default
    private ActivationStatus status = ActivationStatus.ACTIVE;

    public String getRoomCode() {
        if (roomCode != null && !roomCode.isBlank()) {
            return roomCode;
        }
        return maphongkham;
    }

    public String getMaphongkham() {
        return getRoomCode();
    }

    public String getRoomName() {
        if (roomName != null && !roomName.isBlank()) {
            return roomName;
        }
        return phongkham;
    }

    public String getPhongkham() {
        return getRoomName();
    }
}
