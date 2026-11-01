
package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.dto.response.VisitFeePreviewResponse;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
@Tag(name = "Lượt khám", description = "Đăng ký lượt khám và quản lý hàng đợi khám")
public class VisitController {

    private final VisitService visitService;

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @PostMapping
    @Operation(summary = "UC-40 Đăng ký lượt khám")
    public ResponseEntity<ApiResponse<VisitResponse>> createVisit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVisitRequest request
    ) {
        VisitResponse created = visitService.createVisit(StaffPrincipal.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/visits/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<VisitResponse>> getActiveVisit(@RequestParam UUID patientId) {
        VisitResponse activeVisit = visitService.getActiveVisit(patientId);
        return activeVisit == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(ApiResponse.success(activeVisit));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/fee-preview")
    public ApiResponse<VisitFeePreviewResponse> previewFee(
            @RequestParam UUID patientId,
            @RequestParam VisitDoituongbnLoai doituongbnLoai,
            @RequestParam(required = false) String mabhyt) {
        return ApiResponse.success(
                visitService.previewFee(patientId, doituongbnLoai, mabhyt));
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN', 'CASHIER')")
    @GetMapping("/queue")
    @Operation(summary = "UC-42, UC-43 Xem hàng đợi lượt khám")
    public ApiResponse<List<VisitResponse>> getRoomQueue(
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(visitService.getRoomQueue(roomId, date));
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @PostMapping("/{visitId}/start-examination")
    @Operation(summary = "UC-44 Gọi bệnh nhân vào khám")
    public ApiResponse<VisitResponse> startExamination(
            @PathVariable UUID visitId
    ) {
        return ApiResponse.success(visitService.startExamination(visitId));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/queue-status")
    @Operation(summary = "UC-42 Xem tình trạng hàng đợi theo phòng khám")
    public ApiResponse<List<RoomQueueStatusResponse>> getRoomQueueStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(visitService.getRoomQueueStatus(date));
    }
}
