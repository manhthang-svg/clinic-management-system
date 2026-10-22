package clinic.backend.dto.request;

import java.util.UUID;

import clinic.backend.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(

        @NotNull
        UUID departmentId,

        UUID serviceGroupId,

        @NotBlank
        @Size(max = 20)
        String code,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 255)
        String location,

        @NotNull
        RoomType type
) {}