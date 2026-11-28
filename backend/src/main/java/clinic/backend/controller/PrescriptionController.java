package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.request.SubmitPrescriptionRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionPrintResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.PrescriptionPrintService;
import clinic.backend.service.PrescriptionService;
import clinic.backend.service.PrescriptionSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Kê đơn thuốc", description = "UC-59 Kê đơn thuốc, UC-90 Gửi đơn thuốc điện tử, UC-91 Kết quả gửi đơn, UC-60 In đơn thuốc")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionSubmissionService submissionService;
    private final PrescriptionPrintService printService;

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

    @DeleteMapping("/{prescriptionId}")
    @Operation(summary = "UC-59 AF-03: xóa đơn thuốc chưa từng gửi cùng các dòng thuốc")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId) {
        prescriptionService.delete(StaffPrincipal.from(jwt), prescriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{prescriptionId}/submissions")
    @Operation(summary = "UC-91: lịch sử gửi đơn thuốc, mới nhất trước")
    public ApiResponse<List<PrescriptionResponse.Submission>> submissions(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId) {
        return ApiResponse.success(prescriptionService.submissions(StaffPrincipal.from(jwt), prescriptionId));
    }

    @GetMapping("/{prescriptionId}/print")
    @Operation(summary = "UC-60: nội dung đơn thuốc in (TT 26 Phụ lục I) để xem trước; chỉ đơn đã được chấp nhận")
    public ApiResponse<PrescriptionPrintResponse> printPreview(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId) {
        return ApiResponse.success(printService.printPreview(StaffPrincipal.from(jwt), prescriptionId));
    }

    @PostMapping("/{prescriptionId}/commands/prepare-print")
    @Operation(summary = "UC-60: ghi nhận lần in (nhật ký PRINT, printed_at lần đầu) và trả về nội dung đơn in")
    public ApiResponse<PrescriptionPrintResponse> preparePrint(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID prescriptionId) {
        return ApiResponse.success(printService.preparePrint(StaffPrincipal.from(jwt), prescriptionId));
    }

    @PostMapping("/{prescriptionId}/commands/submit")
    @Operation(summary = "UC-90: kiểm tra lại đơn và gửi lên Hệ thống đơn thuốc quốc gia; "
            + "mật khẩu liên thông của bác sĩ chỉ gửi khi máy chủ yêu cầu đăng nhập")
    public ApiResponse<PrescriptionResponse> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID prescriptionId,
            @RequestBody SubmitPrescriptionRequest request) {
        return ApiResponse.success(submissionService.submit(StaffPrincipal.from(jwt), prescriptionId, request));
    }
}
