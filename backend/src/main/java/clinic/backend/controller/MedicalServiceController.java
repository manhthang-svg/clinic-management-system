package clinic.backend.controller;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import clinic.backend.dto.request.CreateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServicePriceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceStatusRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.CreatedMedicalServiceResponse;
import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceFormOptionsResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.MedicalServiceStatusCheckResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.service.MedicalServiceService;
import clinic.backend.security.user.StaffPrincipal;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/services")
@Tag(name = "Quản lý dịch vụ y tế", description = "Quản lý dịch vụ và bảng giá dành cho quản trị viên")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class MedicalServiceController {

    private final MedicalServiceService service;

    @Operation(summary = "UC-21 Xem chi tiết dịch vụ y tế")
    @GetMapping("/{serviceId}")
    public ApiResponse<MedicalServiceDetailResponse> detail(@PathVariable UUID serviceId) {
        return ApiResponse.success(service.getService(serviceId));
    }

    @Operation(summary = "UC-20 Xem và tìm kiếm danh sách dịch vụ, bảng giá")
    @GetMapping
    public ApiResponse<PageResponse<MedicalServiceSummaryResponse>> list(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) MedicalServiceType type,
            @RequestParam(name = "group", required = false) UUID serviceGroupId,
            @RequestParam(name = "department", required = false) UUID departmentId,
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "code,asc")
            @Pattern(regexp = "(?:code|name|type|clinicPrice|insurancePrice|status)(?:,(?:asc|desc))?") String sort) {
        String[] parts = sort.split(",", -1);
        boolean descending = parts.length > 1 && "desc".equals(parts[1].toLowerCase(Locale.ROOT));
        return ApiResponse.success(service.listServices(
                keyword, type, serviceGroupId, departmentId, status, page, size, parts[0], descending));
    }

    @Operation(summary = "Lấy dữ liệu cho biểu mẫu dịch vụ y tế")
    @GetMapping("/form-options")
    public ApiResponse<MedicalServiceFormOptionsResponse> formOptions() {
        return ApiResponse.success(service.getFormOptions());
    }

    @Operation(summary = "Tạo dịch vụ y tế")
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedMedicalServiceResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMedicalServiceRequest request) {
        CreatedMedicalServiceResponse created = service.createService(StaffPrincipal.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/services/" + created.serviceId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "Cập nhật thông tin dịch vụ y tế")
    @PutMapping("/{serviceId}")
    public ApiResponse<MedicalServiceDetailResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateMedicalServiceRequest request) {
        return ApiResponse.success(service.updateService(StaffPrincipal.from(jwt), serviceId, request));
    }

    @Operation(summary = "Cập nhật giá dịch vụ y tế")
    @PutMapping("/{serviceId}/prices")
    public ApiResponse<MedicalServiceDetailResponse> updatePrices(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateMedicalServicePriceRequest request) {
        return ApiResponse.success(service.updatePrices(serviceId, request));
    }

    @Operation(summary = "Kiểm tra điều kiện ngừng hoạt động dịch vụ")
    @GetMapping("/{serviceId}/deactivation-check")
    public ApiResponse<MedicalServiceStatusCheckResponse> checkDeactivation(
            @PathVariable UUID serviceId) {
        return ApiResponse.success(service.checkDeactivation(serviceId));
    }

    @Operation(summary = "Kiểm tra điều kiện kích hoạt dịch vụ")
    @GetMapping("/{serviceId}/activation-check")
    public ApiResponse<MedicalServiceStatusCheckResponse> checkActivation(
            @PathVariable UUID serviceId) {
        return ApiResponse.success(service.checkActivation(serviceId));
    }

    @Operation(summary = "Cập nhật trạng thái dịch vụ y tế")
    @PatchMapping("/{serviceId}/status")
    public ApiResponse<MedicalServiceDetailResponse> updateStatus(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateMedicalServiceStatusRequest request) {
        return ApiResponse.success(service.updateStatus(serviceId, request));
    }

}
