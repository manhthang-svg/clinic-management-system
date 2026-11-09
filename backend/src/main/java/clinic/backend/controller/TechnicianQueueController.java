package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.RejectParaclinicalOrderRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.TechnicianQueueItemResponse;
import clinic.backend.dto.response.TechnicianQueueResponse;
import clinic.backend.dto.response.TechnicianRoomResponse;
import clinic.backend.security.user.StaffPrincipal;
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

    @GetMapping("/queue/{detailId}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Xem chi tiết một ca trong hàng đợi cận lâm sàng")
    public ApiResponse<TechnicianQueueItemResponse> getQueueDetail(@PathVariable UUID detailId) {
        return ApiResponse.success(technicianQueueService.getQueueDetail(detailId));
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
    @Operation(summary = "Hoàn tất kỹ thuật thực hiện")
    public ApiResponse<TechnicianQueueItemResponse> completeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID detailId
    ) {
        UUID technicianId = StaffPrincipal.from(jwt).accountId();
        return ApiResponse.success(technicianQueueService.completeOrder(detailId, technicianId));
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

