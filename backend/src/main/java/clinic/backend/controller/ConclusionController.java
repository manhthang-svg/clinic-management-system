package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.SaveConclusionRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ConclusionResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ConclusionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/api/v1/conclusions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Kết luận khám", description = "UC-56 Kết luận khám bệnh")
public class ConclusionController {

    private final ConclusionService conclusionService;

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
}
