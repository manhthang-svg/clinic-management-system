package clinic.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.LabReferenceRangeRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.LabReferenceRangeResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.LabReferenceRangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lab-parameters/{parameterId}/reference-ranges")
@Tag(name = "Khoảng tham chiếu xét nghiệm")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class LabReferenceRangeController {

    private final LabReferenceRangeService service;

    @Operation(summary = "Danh sách khoảng tham chiếu của một chỉ số")
    @GetMapping
    public ApiResponse<List<LabReferenceRangeResponse>> list(@PathVariable UUID parameterId) {
        return ApiResponse.success(service.list(parameterId));
    }

    @Operation(summary = "Tạo và tùy chọn kích hoạt trực tiếp một khoảng tham chiếu")
    @PostMapping
    public ResponseEntity<ApiResponse<LabReferenceRangeResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID parameterId,
            @Valid @RequestBody LabReferenceRangeRequest request) {
        LabReferenceRangeResponse created = service.create(StaffPrincipal.from(jwt), parameterId, request);
        return ResponseEntity.created(URI.create(
                        "/api/v1/lab-parameters/" + parameterId + "/reference-ranges/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "Cập nhật giá trị bằng một khoảng mới có hiệu lực từ hôm nay; khoảng cũ hết hiệu lực từ hôm qua")
    @PostMapping("/{rangeId}/revisions")
    public ResponseEntity<ApiResponse<LabReferenceRangeResponse>> revise(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID parameterId,
            @PathVariable UUID rangeId,
            @Valid @RequestBody LabReferenceRangeRequest request) {
        LabReferenceRangeResponse revised = service.revise(StaffPrincipal.from(jwt), parameterId, rangeId, request);
        return ResponseEntity.created(URI.create(
                        "/api/v1/lab-parameters/" + parameterId + "/reference-ranges/" + revised.id()))
                .body(ApiResponse.success(revised));
    }

    @Operation(summary = "Cập nhật hoặc bật/tắt trực tiếp một khoảng tham chiếu")
    @PutMapping("/{rangeId}")
    public ApiResponse<LabReferenceRangeResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID parameterId,
            @PathVariable UUID rangeId,
            @Valid @RequestBody LabReferenceRangeRequest request) {
        return ApiResponse.success(service.update(StaffPrincipal.from(jwt), parameterId, rangeId, request));
    }
}
