package clinic.backend.controller;

import java.time.LocalDate;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only statistics (UC-26 → UC-32). Route access is restricted in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Thống kê và báo cáo (Admin)")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "UC-26 Xem bảng thống kê tổng quan trong khoảng ngày (mặc định: đầu tháng đến hôm nay)")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardStatisticsResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getDashboard(from, to));
    }

    @Operation(summary = "UC-27 Thống kê lượt khám theo ngày hoặc tháng, kết quả, đối tượng, tái khám, ưu tiên")
    @GetMapping("/visits")
    public ApiResponse<VisitStatisticsResponse> getVisitStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) StatisticsGroupBy groupBy) {
        return ApiResponse.success(statisticsService.getVisitStatistics(from, to, groupBy));
    }

    @Operation(summary = "UC-28 Thống kê số lượt khám và số bệnh nhân của từng bác sĩ trong khoảng ngày")
    @GetMapping("/doctors")
    public ApiResponse<DoctorStatisticsResponse> getDoctorStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getDoctorStatistics(from, to));
    }
}
