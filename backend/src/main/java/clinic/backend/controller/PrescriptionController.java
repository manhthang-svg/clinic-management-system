package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.RequestSignatureRequest;
import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.dto.response.SigningRequestResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.PrescriptionService;
import clinic.backend.service.PrescriptionSigningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Kê đơn thuốc", description = "UC-59 Kê đơn thuốc, UC-90 Ký số và gửi đơn thuốc điện tử")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionSigningService signingService;

    @GetMapping("/visits/{visitId}")
    @Operation(summary = "Tải đơn thuốc của lượt khám, kèm chẩn đoán kết luận và dị ứng thuốc")
    public ApiResponse<PrescriptionResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID visitId) {
        return ApiResponse.success(prescriptionService.get(StaffPrincipal.from(jwt), visitId));
    }

    @PutMapping("/visits/{visitId}")
    @Operation(summary = "Lưu đơn thuốc (một đơn cho mỗi lượt khám); bệnh án vẫn mở")
    public ApiResponse<PrescriptionResponse> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @RequestBody SavePrescriptionRequest request) {
        return ApiResponse.success(prescriptionService.save(StaffPrincipal.from(jwt), visitId, request));
    }

    @GetMapping("/medicine-search")
    @Operation(summary = "Tìm thuốc thường đang hoạt động để kê đơn (tối đa 20, không phân biệt dấu)")
    public ApiResponse<List<PrescribableMedicineResponse>> searchMedicines(
            @RequestParam @Size(max = 100) String keyword) {
        return ApiResponse.success(prescriptionService.searchMedicines(keyword));
    }

    @PostMapping("/{prescriptionId}/commands/request-signature")
    @Operation(summary = "UC-90: kiểm tra lại đơn, tiền kiểm chứng thư và gửi yêu cầu ký số tới CA")
    public ResponseEntity<ApiResponse<SigningRequestResponse>> requestSignature(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId,
            @RequestBody RequestSignatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                signingService.requestSignature(StaffPrincipal.from(jwt), prescriptionId, request)));
    }

    @GetMapping("/{prescriptionId}/signing-requests/current")
    @Operation(summary = "UC-90 AF-06: yêu cầu ký đang chờ của đơn thuốc (null nếu không có)")
    public ApiResponse<SigningRequestResponse> currentSigningRequest(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId) {
        return ApiResponse.success(signingService.currentRequest(StaffPrincipal.from(jwt), prescriptionId));
    }

    @PostMapping("/{prescriptionId}/signing-requests/{requestId}/commands/complete")
    @Operation(summary = "UC-90: hỏi kết quả ký; khi đã ký thì kiểm tra chữ ký, lưu và gửi đơn")
    public ApiResponse<SigningRequestResponse> completeSigning(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId, @PathVariable UUID requestId) {
        return ApiResponse.success(signingService.complete(StaffPrincipal.from(jwt), prescriptionId, requestId));
    }

    @PostMapping("/{prescriptionId}/signing-requests/{requestId}/commands/cancel")
    @Operation(summary = "UC-90 AF-03: hủy yêu cầu ký đang chờ")
    public ResponseEntity<Void> cancelSigning(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId, @PathVariable UUID requestId) {
        signingService.cancel(StaffPrincipal.from(jwt), prescriptionId, requestId);
        return ResponseEntity.noContent().build();
    }
}
