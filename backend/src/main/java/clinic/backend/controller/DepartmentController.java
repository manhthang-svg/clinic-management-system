package clinic.backend.controller;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.DepartmentRequest;
import clinic.backend.dto.request.RoomRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.DepartmentRoomItemResponse;
import clinic.backend.dto.response.DepartmentStatsResponse;
import clinic.backend.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Department and Room catalog management (Quản lý khoa/phòng).
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Departments & Rooms", description = "Quản lý danh sách khoa chuyên môn và phòng chức năng")
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "Lấy toàn bộ danh sách khoa và phòng kèm người phụ trách")
    @GetMapping("/items")
    public ApiResponse<List<DepartmentRoomItemResponse>> getAllItems() {
        return ApiResponse.success(departmentService.getAllItems());
    }

    @Operation(summary = "Lấy số liệu thống kê khoa và phòng")
    @GetMapping("/stats")
    public ApiResponse<DepartmentStatsResponse> getStats() {
        return ApiResponse.success(departmentService.getStats());
    }

    @Operation(summary = "Tạo mới khoa chuyên môn")
    @PostMapping
    public ApiResponse<DepartmentRoomItemResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.success(departmentService.createDepartment(request));
    }

    @Operation(summary = "Cập nhật thông tin khoa chuyên môn")
    @PutMapping("/{id}")
    public ApiResponse<DepartmentRoomItemResponse> updateDepartment(
            @PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.success(departmentService.updateDepartment(id, request));
    }

    @Operation(summary = "Đổi trạng thái hoạt động của khoa (Hoạt động / Tạm ngưng)")
    @PatchMapping("/{id}/status")
    public ApiResponse<DepartmentRoomItemResponse> toggleDepartmentStatus(@PathVariable UUID id) {
        return ApiResponse.success(departmentService.toggleDepartmentStatus(id));
    }

    @Operation(summary = "Tạo mới phòng khám / phòng chức năng")
    @PostMapping("/rooms")
    public ApiResponse<DepartmentRoomItemResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ApiResponse.success(departmentService.createRoom(request));
    }

    @Operation(summary = "Cập nhật thông tin phòng chức năng")
    @PutMapping("/rooms/{id}")
    public ApiResponse<DepartmentRoomItemResponse> updateRoom(
            @PathVariable UUID id, @Valid @RequestBody RoomRequest request) {
        return ApiResponse.success(departmentService.updateRoom(id, request));
    }

    @Operation(summary = "Đổi trạng thái hoạt động của phòng (Hoạt động / Tạm ngưng)")
    @PatchMapping("/rooms/{id}/status")
    public ApiResponse<DepartmentRoomItemResponse> toggleRoomStatus(@PathVariable UUID id) {
        return ApiResponse.success(departmentService.toggleRoomStatus(id));
    }
}
