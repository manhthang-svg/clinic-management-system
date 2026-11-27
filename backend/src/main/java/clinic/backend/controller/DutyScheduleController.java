package clinic.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.request.DutyScheduleSaveRequest;
import clinic.backend.dto.request.DutyShiftConfigRequest;
import clinic.backend.dto.request.WeeklyBatchScheduleRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.DutyScheduleItemResponse;
import clinic.backend.dto.response.PersonalDutyScheduleResponse;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.dto.response.WeeklyScheduleResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.DutyScheduleService;
import clinic.backend.service.DutyShiftConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@RequestMapping("/api/v1/duty-schedules")
@RequiredArgsConstructor
@Tag(name = "Duty Schedules", description = "Quản lý lịch trực nhân viên theo tuần (Ca sáng & Ca chiều)")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class DutyScheduleController {

    private final DutyScheduleService dutyScheduleService;
    private final DutyShiftConfigService dutyShiftConfigService;

    @Operation(summary = "Lấy cấu hình khung giờ ca trực theo tuần")
    @GetMapping("/shift-config")
    public ApiResponse<DutyShiftConfigResponse> getShiftConfig(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate
    ) {
        return ApiResponse.success(dutyShiftConfigService.getConfigForDate(startDate));
    }

    @Operation(summary = "Cập nhật cấu hình khung giờ ca trực theo tuần")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/shift-config")
    public ApiResponse<DutyShiftConfigResponse> updateShiftConfig(
            @Valid @RequestBody DutyShiftConfigRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(dutyShiftConfigService.updateConfig(request, currentUserId));
    }

    @Operation(summary = "Lấy lịch trực theo tuần")
    @GetMapping
    public ApiResponse<WeeklyScheduleResponse> getWeeklySchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(dutyScheduleService.getWeeklySchedule(startDate, endDate));
    }

    @Operation(summary = "Lấy lịch trực cá nhân")
    @GetMapping("/me")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ApiResponse<PersonalDutyScheduleResponse> getPersonalSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(dutyScheduleService.getPersonalSchedule(
                StaffPrincipal.from(jwt).accountId(), startDate, endDate));
    }

    @Operation(summary = "Lấy lịch trực theo phòng trong tuần")
    @GetMapping("/rooms/{roomId}")
    public ApiResponse<WeeklyScheduleResponse> getWeeklyScheduleByRoom(
            @PathVariable UUID roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(dutyScheduleService.getWeeklyScheduleByRoom(roomId, startDate, endDate));
    }

    @Operation(summary = "Phân công / Cập nhật ca trực đơn lẻ")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<DutyScheduleItemResponse> saveDuty(
            @Valid @RequestBody DutyScheduleSaveRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(dutyScheduleService.saveDuty(request, currentUserId));
    }

    @Operation(summary = "Lưu / Cập nhật toàn bộ lịch trực của tuần (batch)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/weekly")
    public ApiResponse<WeeklyScheduleResponse> batchSaveWeeklySchedule(
            @Valid @RequestBody WeeklyBatchScheduleRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(dutyScheduleService.batchSaveWeeklySchedule(request, currentUserId));
    }

    @Operation(summary = "Xoá ca trực theo ID")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDuty(@PathVariable UUID id) {
        dutyScheduleService.deleteDuty(id);
        return ApiResponse.success(null);
    }
}
