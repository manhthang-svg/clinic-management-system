package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.CancelFollowUpAppointmentRequest;
import clinic.backend.dto.request.SaveFollowUpAppointmentRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.FollowUpAppointmentResponse;
import clinic.backend.dto.response.FollowUpScheduleResponse;
import clinic.backend.dto.response.FollowUpSlipResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.FollowUpAppointmentService;
import clinic.backend.service.FollowUpScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1/follow-up-appointments")
public class FollowUpAppointmentController {

    private static final String DOCTOR = "hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')";

    private final FollowUpAppointmentService service;
    private final FollowUpScheduleService scheduleService;

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ApiResponse<FollowUpAppointmentResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CancelFollowUpAppointmentRequest request) {
        return ApiResponse.success(service.cancel(StaffPrincipal.from(jwt), appointmentId, request));
    }

    @GetMapping("/visits/{visitId}")
    @PreAuthorize(DOCTOR)
    @Operation(summary = "UC-61: form hẹn tái khám của lượt khám, kèm lịch hẹn đã lưu nếu có")
    public ApiResponse<FollowUpScheduleResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID visitId) {
        return ApiResponse.success(scheduleService.get(StaffPrincipal.from(jwt), visitId));
    }

    @PutMapping("/visits/{visitId}")
    @PreAuthorize(DOCTOR)
    @Operation(summary = "UC-61: tạo hoặc sửa lịch hẹn tái khám của lượt khám (mỗi lượt khám một lịch hẹn)")
    public ApiResponse<FollowUpScheduleResponse> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID visitId,
            @RequestBody SaveFollowUpAppointmentRequest request) {
        return ApiResponse.success(scheduleService.save(StaffPrincipal.from(jwt), visitId, request));
    }

    @GetMapping("/{appointmentId}/print")
    @PreAuthorize(DOCTOR)
    @Operation(summary = "UC-62: nội dung phiếu hẹn khám lại (TT 01/2025 Phụ lục V) để xem trước")
    public ApiResponse<FollowUpSlipResponse> printPreview(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId) {
        return ApiResponse.success(scheduleService.printPreview(StaffPrincipal.from(jwt), appointmentId));
    }

    @PostMapping("/{appointmentId}/commands/prepare-print")
    @PreAuthorize(DOCTOR)
    @Operation(summary = "UC-62: ghi nhận lần in (nhật ký PRINT, printed_at lần đầu) và trả về phiếu hẹn")
    public ApiResponse<FollowUpSlipResponse> preparePrint(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId) {
        return ApiResponse.success(scheduleService.preparePrint(StaffPrincipal.from(jwt), appointmentId));
    }
}
