package clinic.backend.dto.response;

import clinic.backend.enums.ParaclinicalOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ParaclinicalOrderResponse(
        UUID id,
        String sophieu,
        UUID visitId,
        UUID paymentId,
        String paymentCode,
        String chandoansobo,
        BigDecimal tongcong,
        ParaclinicalOrderStatus status,
        Instant createdAt,
        List<ParaclinicalOrderDetailResponse> details
) {
    public record ParaclinicalOrderDetailResponse(
            UUID id,
            UUID serviceId,
            String machidinh,
            String tenchidinh,
            BigDecimal soluong,
            BigDecimal dongia,
            BigDecimal thanhtien,
            UUID roomId,
            String status
    ) {
    }
}
