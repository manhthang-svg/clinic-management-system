package clinic.backend.dto.request;

import java.util.UUID;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Size(min = 2, max = 20, message = "Mã phòng phải từ 2 đến 20 ký tự")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Mã phòng chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)")
    private String roomCode;

    @Size(min = 2, max = 20, message = "Mã phòng phải từ 2 đến 20 ký tự")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Mã phòng chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)")
    private String maphongkham;

    @Size(min = 2, max = 100, message = "Tên phòng phải từ 2 đến 100 ký tự")
    private String roomName;

    @Size(min = 2, max = 100, message = "Tên phòng phải từ 2 đến 100 ký tự")
    private String phongkham;

    @NotNull(message = "Khoa trực thuộc không được để trống")
    private UUID departmentId;

    private UUID serviceGroupId;

    @Size(min = 2, max = 255, message = "Vị trí buồng phải từ 2 đến 255 ký tự")
    private String location;

    @NotNull(message = "Loại phòng không được để trống")
    private RoomType type;

    private String headPerson;

    @Builder.Default
    private ActivationStatus status = ActivationStatus.ACTIVE;

    private Long version;

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
