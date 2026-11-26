package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.CancelParaclinicalServiceRequest;
import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ParaclinicalCancellationResponse;
import clinic.backend.dto.response.ParaclinicalOrderPrintResponse;
import clinic.backend.dto.response.ParaclinicalOrderResponse;
import clinic.backend.dto.response.ParaclinicalServiceOptionResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ParaclinicalOrderPrintService;
import clinic.backend.service.ParaclinicalOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/paraclinical-orders")
@RequiredArgsConstructor
@Tag(name = "Chỉ định cận lâm sàng", description = "Quản lý chỉ định và danh mục dịch vụ cận lâm sàng")
public class ParaclinicalOrderController {

    private final ParaclinicalOrderService paraclinicalOrderService;
    private final ParaclinicalOrderPrintService paraclinicalOrderPrintService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "UC-49 Tạo chỉ định cận lâm sàng")
    public ApiResponse<ParaclinicalOrderResponse> createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateParaclinicalOrderRequest request
    ) {
        return ApiResponse.success(
                paraclinicalOrderService.createOrder(StaffPrincipal.from(jwt).accountId(), request));
    }

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Danh sách chỉ định cận lâm sàng của một lượt khám")
    public ApiResponse<List<ParaclinicalOrderResponse>> listOrdersByVisit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID visitId
    ) {
        return ApiResponse.success(
                paraclinicalOrderService.listOrdersByVisit(StaffPrincipal.from(jwt).accountId(), visitId));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'CASHIER')")
    @Operation(summary = "Xem chi tiết chỉ định cận lâm sàng")
    public ApiResponse<ParaclinicalOrderResponse> getOrderById(
            @PathVariable UUID orderId
    ) {
        return ApiResponse.success(paraclinicalOrderService.getOrderById(orderId));
    }

    @PostMapping("/{orderId}/commands/prepare-print")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "UC-50 In phiếu chỉ định cận lâm sàng")
    public ApiResponse<ParaclinicalOrderPrintResponse> preparePrint(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        return ApiResponse.success(
                paraclinicalOrderPrintService.preparePrint(StaffPrincipal.from(jwt).accountId(), orderId));
    }

    @PostMapping("/details/{orderDetailId}/commands/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "BR-25, BR-41 Hủy dịch vụ cận lâm sàng bệnh nhân từ chối")
    public ApiResponse<ParaclinicalCancellationResponse> cancelService(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderDetailId,
            @Valid @RequestBody CancelParaclinicalServiceRequest request
    ) {
        return ApiResponse.success(paraclinicalOrderService.cancelService(
                StaffPrincipal.from(jwt).accountId(), orderDetailId, request));
    }

    @GetMapping("/services")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "UC-49 Danh sách dịch vụ cận lâm sàng đang hoạt động")
    public ApiResponse<List<ParaclinicalServiceOptionResponse>> getParaclinicalServices() {
        return ApiResponse.success(paraclinicalOrderService.listOrderableServices());
    }
}
