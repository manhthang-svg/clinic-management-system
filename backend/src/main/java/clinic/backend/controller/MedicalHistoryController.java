package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.UpdateMedicalHistoryRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.MedicalHistoryResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.MedicalHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-39 Update Medical History and Allergies. Only the Doctor reads and changes them (BR-23).
 */
@RestController
@RequestMapping("/api/v1/visits/{visitId}/medical-history")
@RequiredArgsConstructor
@Tag(name = "Tiền sử và dị ứng", description = "Tiền sử bệnh và dị ứng thuốc của bệnh nhân trong lượt khám")
public class MedicalHistoryController {

    private final MedicalHistoryService medicalHistoryService;

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "UC-39 Xem tiền sử và dị ứng thuốc của bệnh nhân trong lượt khám")
    public ApiResponse<MedicalHistoryResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId
    ) {
        return ApiResponse.success(medicalHistoryService.getForVisit(StaffPrincipal.from(jwt).accountId(), visitId));
    }

    @PutMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "UC-39 Cập nhật tiền sử và dị ứng thuốc")
    public ApiResponse<MedicalHistoryResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @Valid @RequestBody UpdateMedicalHistoryRequest request
    ) {
        return ApiResponse.success(medicalHistoryService.updateForVisit(
                StaffPrincipal.from(jwt).accountId(), visitId, request));
    }
}
