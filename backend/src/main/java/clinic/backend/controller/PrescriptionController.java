package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.SavePrescriptionRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PrescribableMedicineResponse;
import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Tag(name = "Kê đơn thuốc", description = "UC-59 Kê đơn thuốc")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

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

    @GetMapping("/medicines")
    @Operation(summary = "Tìm thuốc thường đang hoạt động để kê đơn")
    public ApiResponse<List<PrescribableMedicineResponse>> searchMedicines(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit) {
        return ApiResponse.success(prescriptionService.searchMedicines(keyword, limit));
    }
}
