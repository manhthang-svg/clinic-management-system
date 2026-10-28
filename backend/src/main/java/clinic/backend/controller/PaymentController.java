package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.ConfirmPayOSResultRequest;
import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PaymentQrResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.service.PayOSPaymentService;
import clinic.backend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Thanh toán", description = "Quản lý phiếu thu và xác nhận thanh toán")
public class PaymentController {

    private final PayOSPaymentService payOSPaymentService;
    private final PaymentService paymentService;

    // 1. Danh sách phiếu thu (có lọc theo trạng thái và loại thanh toán)
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping
    @Operation(summary = "UC-69, UC-70, UC-73 Xem và tra cứu phiếu thu")
    public ApiResponse<Page<PaymentResponse>> getPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return ApiResponse.success(paymentService.getPayments(status, type, search, pageable));
    }

    // 2. Danh sách phiếu thu đang chờ thanh toán
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/pending")
    @Operation(summary = "UC-69 Xem danh sách phiếu thu chờ thanh toán")
    public ApiResponse<Page<PaymentResponse>> getPendingPayments(
            @RequestParam(required = false) PaymentType type,
            Pageable pageable
    ) {
        return ApiResponse.success(
                paymentService.getPayments(PaymentStatus.PENDING, type, null, pageable));
    }

    // 3. Chi tiết phiếu thu
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/{paymentId}")
    @Operation(summary = "UC-70 Xem chi tiết phiếu thu")
    public ApiResponse<PaymentResponse> getPaymentById(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(paymentService.getPaymentById(paymentId));
    }

    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/{paymentId}/print")
    @Operation(summary = "UC-72 Chuẩn bị dữ liệu in phiếu thu")
    public ApiResponse<PaymentResponse> getPrintablePayment(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(paymentService.getPrintablePayment(paymentId));
    }

    // 4. Xác nhận thanh toán tại quầy (Tiền mặt / Thẻ)
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/{paymentId}/confirm")
    @Operation(summary = "UC-71 Xác nhận thanh toán")
    public ApiResponse<PaymentResponse> confirmPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return ApiResponse.success(paymentService.confirmPayment(paymentId, request));
    }

    // 5. Tạo mã QR PayOS
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/{paymentId}/payos-qr")
    @Operation(summary = "Tạo mã QR thanh toán PayOS")
    public ApiResponse<PaymentQrResponse> createQr(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(payOSPaymentService.createQr(paymentId));
    }

    // 6. Đồng bộ / kiểm tra trạng thái thanh toán từ PayOS
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/{paymentId}/sync-payos")
    @Operation(summary = "Đồng bộ trạng thái thanh toán PayOS")
    public ApiResponse<PaymentQrResponse> syncPayOS(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(payOSPaymentService.syncPayOSStatus(paymentId));
    }

    // 7. Xác nhận kết quả thanh toán PayOS từ trang chuyển hướng kết quả (Return URL)
    @PostMapping("/confirm-payos-result")
    @Operation(summary = "Xác nhận kết quả thanh toán PayOS")
    public ApiResponse<PaymentResponse> confirmPayOSResult(
            @Valid @RequestBody ConfirmPayOSResultRequest request
    ) {
        UUID paymentId = payOSPaymentService.confirmPayOSResult(request);
        return ApiResponse.success(paymentService.getPaymentById(paymentId));
    }

    // 8. Tra cứu phiếu thu theo mã đơn hàng PayOS
    @GetMapping("/by-order-code/{orderCode}")
    @Operation(summary = "Tra cứu phiếu thu theo mã đơn hàng PayOS")
    public ApiResponse<PaymentResponse> getPaymentByOrderCode(
            @PathVariable Long orderCode
    ) {
        UUID paymentId = payOSPaymentService.getPaymentIdByOrderCode(orderCode);
        return ApiResponse.success(paymentService.getPaymentById(paymentId));
    }
}
