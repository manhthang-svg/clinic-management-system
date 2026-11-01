package clinic.backend.dto.request;

import java.util.UUID;

import clinic.backend.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(

        @NotNull
        UUID departmentId,

        UUID serviceGroupId,

        @NotBlank(message = "Mã phòng không được để trống")
        @Size(min = 2, max = 20, message = "Mã phòng phải từ 2 đến 20 ký tự")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "Mã phòng chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)")
        String code,

        @NotBlank(message = "Tên phòng không được để trống")
        @Size(min = 2, max = 100, message = "Tên phòng phải từ 2 đến 100 ký tự")
        String name,

        @NotBlank(message = "Vị trí không được để trống")
        @Size(min = 2, max = 255, message = "Vị trí buồng phải từ 2 đến 255 ký tự")
        String location,

        @NotNull
        RoomType type
) {}