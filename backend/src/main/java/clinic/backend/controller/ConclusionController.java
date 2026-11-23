package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.ConfirmConclusionRequest;
import clinic.backend.dto.request.SaveConclusionRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ConclusionClosureResponse;
import clinic.backend.dto.response.ConclusionConfirmationResponse;
import clinic.backend.dto.response.ConclusionResponse;
import clinic.backend.dto.response.ConclusionSlipResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ConclusionPrintService;
import clinic.backend.service.ConclusionService;
import clinic.backend.service.MedicalRecordClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conclusions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Kết luận khám", description = "UC-56 Kết luận khám bệnh, UC-58 Đóng hồ sơ bệnh án")
public class ConclusionController {

    private final ConclusionService conclusionService;
    private final MedicalRecordClosureService closureService;
    private final ConclusionPrintService conclusionPrintService;

    @GetMapping("/visits/{visitId}/print")
    @Operation(summary = "BF-04: nội dung phiếu kết luận khám để in (không ghi log)")
    public ApiResponse<ConclusionSlipResponse> print(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID visitId) {
        return ApiResponse.success(conclusionPrintService.print(StaffPrincipal.from(jwt), visitId));
    }

    @GetMapping("/visits/{visitId}")
    @Operation(summary = "Tải biểu mẫu kết luận của lượt khám, kèm dữ liệu đã lưu")
    public ApiResponse<ConclusionResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID visitId) {
        return ApiResponse.success(conclusionService.get(StaffPrincipal.from(jwt), visitId));
    }

    @PutMapping("/visits/{visitId}")
    @Operation(summary = "Lưu kết luận; bệnh án vẫn mở")
    public ApiResponse<ConclusionResponse> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @RequestBody SaveConclusionRequest request) {
        return ApiResponse.success(conclusionService.save(StaffPrincipal.from(jwt), visitId, request));
    }

    @GetMapping("/visits/{visitId}/closure")
    @Operation(summary = "UC-58: điều kiện để xác nhận kết luận và đóng hồ sơ")
    public ApiResponse<ConclusionClosureResponse> closure(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID visitId) {
        return ApiResponse.success(closureService.closureCheck(StaffPrincipal.from(jwt), visitId));
    }

    @PostMapping("/visits/{visitId}/commands/confirm")
    @Operation(summary = "UC-58: xác nhận kết luận, đóng và lưu trữ hồ sơ, lượt khám hoàn thành")
    public ApiResponse<ConclusionConfirmationResponse> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @RequestBody ConfirmConclusionRequest request) {
        return ApiResponse.success(closureService.confirm(StaffPrincipal.from(jwt), visitId, request));
    }
}
