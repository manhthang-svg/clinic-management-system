package clinic.backend.controller;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import clinic.backend.dto.request.CreateMedicineRequest;
import clinic.backend.dto.request.UpdateMedicineRequest;
import clinic.backend.dto.request.UpdateMedicineStatusRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.CreatedMedicineResponse;
import clinic.backend.dto.response.MedicineDetailResponse;
import clinic.backend.dto.response.MedicineFormOptionsResponse;
import clinic.backend.dto.response.MedicineSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.MedicineCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/medicines")
@Tag(name = "Danh mục thuốc", description = "Quản lý danh mục thuốc dành cho quản trị viên")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class MedicineController {

    private final MedicineCatalogService service;

    @Operation(summary = "UC-81 Xem và tìm kiếm danh mục thuốc")
    @GetMapping
    public ApiResponse<PageResponse<MedicineSummaryResponse>> list(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 50) String unit,
            @RequestParam(required = false) @Size(max = 50) String route,
            @RequestParam(required = false) MedicineControlGroup controlGroup,
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc")
            @Pattern(regexp = "(?:name|code|activeIngredient|status)(?:,(?:asc|desc))?") String sort) {
        String[] parts = sort.split(",", -1);
        boolean descending = parts.length > 1 && "desc".equals(parts[1].toLowerCase(Locale.ROOT));
        return ApiResponse.success(service.listMedicines(
                keyword, unit, route, controlGroup, status, page, size, parts[0], descending));
    }

    @Operation(summary = "UC-81 Xem chi tiết thuốc")
    @GetMapping("/{medicineId}")
    public ApiResponse<MedicineDetailResponse> detail(@PathVariable UUID medicineId) {
        return ApiResponse.success(service.getMedicine(medicineId));
    }

    @Operation(summary = "Lấy danh sách đường dùng và đơn vị tính cho biểu mẫu thuốc")
    @GetMapping("/form-options")
    public ApiResponse<MedicineFormOptionsResponse> formOptions() {
        return ApiResponse.success(service.getFormOptions());
    }

    @Operation(summary = "UC-82 Tạo thuốc")
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedMedicineResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMedicineRequest request) {
        CreatedMedicineResponse created = service.createMedicine(StaffPrincipal.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/medicines/" + created.medicineId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "UC-83 Cập nhật thông tin thuốc")
    @PutMapping("/{medicineId}")
    public ApiResponse<MedicineDetailResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID medicineId,
            @Valid @RequestBody UpdateMedicineRequest request) {
        return ApiResponse.success(service.updateMedicine(StaffPrincipal.from(jwt), medicineId, request));
    }

    @Operation(summary = "UC-84 Kích hoạt hoặc ngừng sử dụng thuốc")
    @PatchMapping("/{medicineId}/status")
    public ApiResponse<MedicineDetailResponse> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID medicineId,
            @Valid @RequestBody UpdateMedicineStatusRequest request) {
        return ApiResponse.success(service.updateStatus(StaffPrincipal.from(jwt), medicineId, request));
    }
}
