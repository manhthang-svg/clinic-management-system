package clinic.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmPayOSResultRequest;
import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PayOSResultResponse;
import clinic.backend.dto.response.PaymentHistoryResponse;
import clinic.backend.dto.response.PaymentQrResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.dto.response.ReceiptPrintResponse;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.PayOSPaymentService;
import clinic.backend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Thanh toán", description = "Quản lý phiếu thu và xác nhận thanh toán")
public class PaymentController {

    private final PayOSPaymentService payOSPaymentService;
    private final PaymentService paymentService;

    // UC-70: danh sach / tim kiem phieu thu (loc trang thai, loai, khoang ngay)
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping
    @Operation(summary = "UC-69, UC-70 Xem và tìm kiếm phiếu thu")
    public ApiResponse<Page<PaymentResponse>> getPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable
    ) {
        return ApiResponse.success(paymentService.getPayments(status, type, search, from, to, pageable));
    }

    // UC-73: lich su thanh toan + tong theo phuong thuc
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/history")
    @Operation(summary = "UC-73 Xem lịch sử thanh toán")
    public ApiResponse<PaymentHistoryResponse> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean mine,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "paidAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID cashierId = mine ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(
                paymentService.getHistory(cashierId, status, type, method, search, from, to, pageable));
    }

    // UC-69: phieu thu dang cho thanh toan
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/pending")
    @Operation(summary = "UC-69 Xem danh sách phiếu thu chờ thanh toán")
    public ApiResponse<Page<PaymentResponse>> getPendingPayments(
            @RequestParam(required = false) PaymentType type,
            Pageable pageable
    ) {
        return ApiResponse.success(
                paymentService.getPayments(PaymentStatus.PENDING, type, null, null, null, pageable));
    }

    // chi tiet 1 phieu thu
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/{paymentId}")
    @Operation(summary = "UC-70 Xem chi tiết phiếu thu")
    public ApiResponse<PaymentResponse> getPaymentById(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(paymentService.getPaymentById(paymentId));
    }

    @PreAuthorize("hasRole('CASHIER')")
    // UC-72: chuan bi in / in lai phieu thu (ghi audit)
    @PostMapping("/{paymentId}/commands/prepare-receipt-print")
    @Operation(summary = "UC-72 Chuẩn bị in hoặc in lại phiếu thu và ghi audit")
    public ApiResponse<ReceiptPrintResponse> prepareReceiptPrint(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(
                paymentService.prepareReceiptPrint(StaffPrincipal.from(jwt).accountId(), paymentId));
    }

    // UC-71: xac nhan thu tien tai quay
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/{paymentId}/confirm")
    @Operation(summary = "UC-71 Xác nhận thanh toán")
    public ApiResponse<PaymentResponse> confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return ApiResponse.success(
                paymentService.confirmPayment(StaffPrincipal.from(jwt).accountId(), paymentId, request));
    }

    // UC-71: tao ma QR PayOS
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/{paymentId}/payos-qr")
    @Operation(summary = "Tạo mã QR thanh toán PayOS")
    public ApiResponse<PaymentQrResponse> createQr(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(payOSPaymentService.createQr(paymentId));
    }

    // hoi PayOS trang thai thanh toan
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/{paymentId}/sync-payos")
    @Operation(summary = "Đồng bộ trạng thái thanh toán PayOS")
    public ApiResponse<PaymentQrResponse> syncPayOS(
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(payOSPaymentService.syncPayOSStatus(paymentId));
    }

    // xac nhan ket qua PayOS khi quay ve tu trang thanh toan
    @PostMapping("/confirm-payos-result")
    @Operation(summary = "Xác nhận kết quả thanh toán PayOS")
    public ApiResponse<PayOSResultResponse> confirmPayOSResult(
            @Valid @RequestBody ConfirmPayOSResultRequest request
    ) {
        UUID paymentId = payOSPaymentService.confirmPayOSResult(request);
        return ApiResponse.success(payOSPaymentService.getResult(paymentId));
    }

    // tim phieu thu theo ma don PayOS
    @GetMapping("/by-order-code/{orderCode}")
    @Operation(summary = "Tra cứu phiếu thu theo mã đơn hàng PayOS")
    public ApiResponse<PayOSResultResponse> getPaymentByOrderCode(
            @PathVariable Long orderCode
    ) {
        UUID paymentId = payOSPaymentService.getPaymentIdByOrderCode(orderCode);
        return ApiResponse.success(payOSPaymentService.getResult(paymentId));
    }
}
