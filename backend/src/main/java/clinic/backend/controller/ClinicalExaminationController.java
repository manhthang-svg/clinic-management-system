package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.MedicalHistoryItemRequest;
import clinic.backend.dto.request.SaveClinicalExaminationRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ClinicalExaminationResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.MedicalHistoryItemResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ClinicalExaminationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@Validated
@RequestMapping("/api/v1/clinical-examinations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Khám lâm sàng", description = "UC-47 Thực hiện khám lâm sàng")
public class ClinicalExaminationController {

    private final ClinicalExaminationService clinicalExaminationService;

    @PostMapping("/visits/{visitId}/open")
    @Operation(summary = "Mở hoặc tải hồ sơ khám lâm sàng hiện tại")
    public ApiResponse<ClinicalExaminationResponse> open(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId) {
        return ApiResponse.success(clinicalExaminationService.open(StaffPrincipal.from(jwt), visitId));
    }

    @PutMapping("/visits/{visitId}")
    @Operation(summary = "Lưu nháp hoặc lưu và tiếp tục hồ sơ khám lâm sàng")
    public ApiResponse<ClinicalExaminationResponse> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @Valid @RequestBody SaveClinicalExaminationRequest request) {
        return ApiResponse.success(clinicalExaminationService.save(StaffPrincipal.from(jwt), visitId, request));
    }

    @PostMapping("/visits/{visitId}/medical-histories")
    @Operation(summary = "Thêm tiền sử hoặc dị ứng thuốc trong lúc khám (UC-39)")
    public ApiResponse<List<MedicalHistoryItemResponse>> addMedicalHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @Valid @RequestBody MedicalHistoryItemRequest request) {
        return ApiResponse.success(
                clinicalExaminationService.addMedicalHistory(StaffPrincipal.from(jwt), visitId, request));
    }

    @PutMapping("/visits/{visitId}/medical-histories/{historyId}")
    @Operation(summary = "Cập nhật tiền sử hoặc dị ứng thuốc trong lúc khám (UC-39)")
    public ApiResponse<List<MedicalHistoryItemResponse>> updateMedicalHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @PathVariable UUID historyId,
            @Valid @RequestBody MedicalHistoryItemRequest request) {
        return ApiResponse.success(clinicalExaminationService.updateMedicalHistory(
                StaffPrincipal.from(jwt), visitId, historyId, request));
    }

    @GetMapping("/icd10")
    @Operation(summary = "Tìm mã ICD-10 đang hoạt động")
    public ApiResponse<List<Icd10OptionResponse>> searchIcd10(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit) {
        return ApiResponse.success(clinicalExaminationService.searchActiveIcd10(keyword, limit));
    }
}
