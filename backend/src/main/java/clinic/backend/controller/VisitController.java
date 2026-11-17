
package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.request.TransferVisitRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.TransferRoomOptionResponse;
import clinic.backend.dto.response.VisitTransferResponse;
import clinic.backend.dto.response.DoctorQueueResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.dto.response.VisitFeePreviewResponse;
import clinic.backend.dto.response.VisitTicketResponse;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.DoctorQueueTab;
import clinic.backend.enums.VisitStatus;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.VisitService;
import clinic.backend.service.VisitTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.net.URI;

@RestController
@Validated
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
@Tag(name = "Lượt khám", description = "Đăng ký lượt khám và quản lý hàng đợi khám")
public class VisitController {

    private final VisitService visitService;
    private final VisitTransferService visitTransferService;

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

    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/queue")
    @Operation(summary = "UC-42, UC-43 Xem hàng đợi lượt khám")
    public ApiResponse<List<VisitResponse>> getRoomQueue(
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(visitService.getRoomQueue(roomId, date));
    }

    @PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @GetMapping("/doctor-queue")
    @Operation(summary = "UC-43 Xem danh sách bệnh nhân của phòng khám được phân công")
    public ApiResponse<DoctorQueueResponse> getDoctorQueue(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "WAITING") DoctorQueueTab tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean priority,
            @RequestParam(required = false) VisitDoituongbnLoai visitType,
            @RequestParam(required = false) VisitStatus interruptedStage,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(visitService.getDoctorQueue(
                StaffPrincipal.from(jwt), tab, keyword, priority, visitType, interruptedStage, page, size));
    }

    @PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @PostMapping("/{visitId}/start-examination")
    @Operation(summary = "UC-44 Gọi bệnh nhân vào khám")
    public ApiResponse<VisitResponse> startExamination(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId
    ) {
        return ApiResponse.success(visitService.startExamination(StaffPrincipal.from(jwt), visitId));
    }

    @PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @PostMapping("/{visitId}/skip")
    @Operation(summary = "UC-44 Đánh dấu bệnh nhân vắng")
    public ApiResponse<VisitResponse> skipVisit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId) {
        return ApiResponse.success(visitService.skipVisit(StaffPrincipal.from(jwt), visitId));
    }

    @PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @PostMapping("/{visitId}/accept-skipped")
    @Operation(summary = "UC-46 Tiếp nhận lại bệnh nhân vắng")
    public ApiResponse<VisitResponse> acceptSkippedVisit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId) {
        return ApiResponse.success(visitService.acceptSkippedVisit(StaffPrincipal.from(jwt), visitId));
    }

    @PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @GetMapping("/{visitId}/transfer-rooms")
    @Operation(summary = "UC-48 Danh sách phòng khám có thể nhận bệnh nhân chuyển đến")
    public ApiResponse<List<TransferRoomOptionResponse>> getTransferRooms(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId) {
        return ApiResponse.success(visitTransferService.getTransferRooms(StaffPrincipal.from(jwt), visitId));
    }

    @PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @PostMapping("/{visitId}/transfer")
    @Operation(summary = "UC-48 Chuyển bệnh nhân sang phòng khám khác")
    public ApiResponse<VisitTransferResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @Valid @RequestBody TransferVisitRequest request) {
        return ApiResponse.success(visitTransferService.transfer(StaffPrincipal.from(jwt), visitId, request));
    }

    // UC-42: tinh trang hang doi theo phong kham
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/queue-status")
    @Operation(summary = "UC-42 Xem tình trạng hàng đợi theo phòng khám")
    public ApiResponse<List<RoomQueueStatusResponse>> getRoomQueueStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(visitService.getRoomQueueStatus(date));
    }

    // I1-D48: the Cashier prints the ticket with the queue number once the examination fee is paid.
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'CASHIER')")
    @GetMapping("/{visitId}/ticket")
    @Operation(summary = "UC-41 Lấy dữ liệu phiếu khám")
    public ApiResponse<VisitTicketResponse> getTicket(@PathVariable UUID visitId) {
        return ApiResponse.success(visitService.getTicket(visitId));
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'CASHIER')")
    @PostMapping("/{visitId}/commands/prepare-ticket-print")
    @Operation(summary = "UC-41 Chuẩn bị in hoặc in lại phiếu khám và ghi audit (bác sĩ: UC-48 AF-02; thu ngân: phiếu số thứ tự, I1-D48)")
    public ApiResponse<VisitTicketResponse> prepareTicketPrint(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId) {
        return ApiResponse.success(
                visitService.prepareTicketPrint(StaffPrincipal.from(jwt), visitId));
    }
}
