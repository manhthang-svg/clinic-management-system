import java.util.UUID;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.ParaclinicalOrderResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.service.MedicalServiceService;
import clinic.backend.service.ParaclinicalOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/paraclinical-orders")
@RequiredArgsConstructor
@Tag(name = "Chỉ định cận lâm sàng", description = "Quản lý chỉ định và danh mục dịch vụ cận lâm sàng")
public class ParaclinicalOrderController {

    private final ParaclinicalOrderService paraclinicalOrderService;
    private final MedicalServiceService medicalServiceService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "UC-49 Tạo chỉ định cận lâm sàng")
    public ApiResponse<ParaclinicalOrderResponse> createOrder(
            @Valid @RequestBody CreateParaclinicalOrderRequest request
    ) {
        return ApiResponse.success(paraclinicalOrderService.createOrder(request));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'TECHNICIAN', 'CASHIER')")
    @Operation(summary = "Xem chi tiết chỉ định cận lâm sàng")
    public ApiResponse<ParaclinicalOrderResponse> getOrderById(
            @PathVariable UUID orderId
    ) {
        return ApiResponse.success(paraclinicalOrderService.getOrderById(orderId));
    }

    @GetMapping("/services")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Xem danh sách dịch vụ cận lâm sàng đang hoạt động")
    public ApiResponse<PageResponse<MedicalServiceSummaryResponse>> getParaclinicalServices() {
        return ApiResponse.success(medicalServiceService.listServices(
                null,
                null,
                null,
                null,
                ActivationStatus.ACTIVE,
                0,
                100,
                "code",
                false
        ));
    }
}
