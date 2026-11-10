package clinic.backend.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-49: the services to order, each with the paraclinical room the Doctor chose for it (I1-D47).
 */
public record CreateParaclinicalOrderRequest(
        @NotNull(message = "Lượt khám không được để trống")
        UUID visitId,
        @Size(max = 500, message = "Chẩn đoán sơ bộ không quá 500 ký tự")
        String chandoansobo,
        String mucdochidinh,
        @NotEmpty(message = "Danh sách dịch vụ chỉ định không được để trống")
        List<@Valid @NotNull Item> items
) {

    /**
     * One ordered service. {@code roomId} is the chosen performing room; when it is empty the least busy Active room
     * of the service's group is used (BR-50-01 default).
     */
    public record Item(
            @NotNull(message = "Dịch vụ không được để trống")
            UUID serviceId,
            UUID roomId
    ) {
    }
}
