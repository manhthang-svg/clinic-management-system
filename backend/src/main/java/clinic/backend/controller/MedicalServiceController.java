package clinic.backend.controller;

import java.util.Locale;
import java.util.UUID;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.service.MedicalServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(required = false) String keyword,
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

}
