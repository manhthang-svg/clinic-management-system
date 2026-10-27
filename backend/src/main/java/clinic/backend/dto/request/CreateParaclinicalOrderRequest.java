package clinic.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateParaclinicalOrderRequest(

        @NotNull(message = "Lượt khám không được để trống")
        UUID visitId,

        @Size(max = 500, message = "Chẩn đoán sơ bộ không quá 500 ký tự")
        String chandoansobo,

        String mucdochidinh,

        @NotEmpty(message = "Danh sách dịch vụ chỉ định không được để trống")
        List<UUID> serviceIds
) {
}
