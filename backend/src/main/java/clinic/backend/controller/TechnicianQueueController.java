package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.request.ReturnParaclinicalOrderToWaitingRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.TechnicianDashboardResponse;
import clinic.backend.dto.response.TechnicianPendingResultResponse;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.TechnicianDashboardService;
import clinic.backend.service.TechnicianQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/technician")
@RequiredArgsConstructor
@Tag(name = "Kỹ thuật viên - Hàng đợi CLS", description = "Quản lý danh sách chờ và phân loại cận lâm sàng")
public class TechnicianQueueController {

    private final TechnicianQueueService technicianQueueService;
    private final TechnicianDashboardService technicianDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Tổng quan của kỹ thuật viên: số liệu hôm nay, lượt chờ tiếp theo và ca chờ nhập kết quả của một phòng")
    public ApiResponse<TechnicianDashboardResponse> getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID roomId
    ) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(technicianDashboardService.getDashboard(roomId, currentUserId));
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Lấy danh sách phòng máy cận lâm sàng")
    public ApiResponse<List<TechnicianRoomResponse>> getRooms(@AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(technicianQueueService.getTechnicianRooms(currentUserId));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Lấy danh sách hàng đợi và số liệu thống kê phòng cận lâm sàng")
    public ApiResponse<TechnicianQueueResponse> getQueue(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String doctorRoom
    ) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(technicianQueueService.getQueue(roomId, search, status, doctorRoom, currentUserId));
    }

    @GetMapping({"/results", "/pending-results"})
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Lấy danh sách ca chờ nhập kết quả và bản nháp cận lâm sàng")
    public ApiResponse<TechnicianPendingResultResponse> getPendingResults(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String doctorRoom
    ) {
        UUID currentUserId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(technicianQueueService.getPendingResults(roomId, search, status, doctorRoom, currentUserId));
    }

    @GetMapping("/queue/{detailId}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Xem chi tiết một ca trong hàng đợi cận lâm sàng")
    public ApiResponse<TechnicianQueueItemResponse> getQueueDetail(@PathVariable UUID detailId) {
        return ApiResponse.success(technicianQueueService.getQueueDetail(detailId));
    }

    @PostMapping("/queue/{detailId}/check-in")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Xác nhận bệnh nhân có mặt tại phòng cận lâm sàng (cấp số thứ tự hàng đợi)")
    public ApiResponse<TechnicianQueueItemResponse> checkInOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.checkInOrder(detailId, technicianId));
    }

    @PostMapping("/queue/{detailId}/accept")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Tiếp nhận bệnh nhân vào phòng máy")
    public ApiResponse<TechnicianQueueItemResponse> acceptOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.acceptOrder(detailId, technicianId));
    }

    @PostMapping("/queue/{detailId}/skip")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Đánh dấu bỏ qua ca trực do bệnh nhân vắng mặt")
    public ApiResponse<TechnicianQueueItemResponse> skipOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.skipOrder(detailId, technicianId));
    }

    @PostMapping("/queue/{detailId}/unskip")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Gọi lại ca trực đã bỏ qua")
    public ApiResponse<TechnicianQueueItemResponse> unskipOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.unskipOrder(detailId, technicianId));
    }

    @PostMapping("/queue/{detailId}/complete")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "UC-66 Xác nhận đã thực hiện chỉ định cận lâm sàng")
    public ApiResponse<TechnicianQueueItemResponse> completeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.completeOrder(detailId, technicianId));
    }

    @PostMapping("/queue/{detailId}/return-to-waiting")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "UC-66 AF-02 Trả chỉ định về hàng chờ kèm lý do")
    public ApiResponse<TechnicianQueueItemResponse> returnToWaiting(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId,
            @Valid @RequestBody ReturnParaclinicalOrderToWaitingRequest request
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.returnToWaiting(detailId, technicianId, request));
    }

    @PostMapping("/queue/{detailId}/reject")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Từ chối thực hiện chỉ định cận lâm sàng kèm lý do")
    public ApiResponse<TechnicianQueueItemResponse> rejectOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId,
            @Valid @RequestBody RejectParaclinicalOrderRequest request
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.rejectOrder(detailId, technicianId, request));
    }
}

