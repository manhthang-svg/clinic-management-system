package clinic.backend.controller;

import java.time.LocalDate;
import java.util.Set;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.DashboardStatisticsResponse;
import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DiagnosisStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse;
import clinic.backend.dto.response.RevenueStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.enums.StatisticsReport;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.StatisticsExportService;
import clinic.backend.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API thong ke cho admin (UC-26 den UC-32) va xuat bao cao (UC-33)
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Thống kê và báo cáo (Admin)")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final StatisticsExportService statisticsExportService;

    // UC-26: tong quan
    @Operation(summary = "UC-26 Xem bảng thống kê tổng quan trong khoảng ngày (mặc định: đầu tháng đến hôm nay)")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardStatisticsResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getDashboard(from, to));
    }

    // UC-27: thong ke luot kham
    @Operation(summary = "UC-27 Thống kê lượt khám theo ngày hoặc tháng, kết quả, đối tượng, tái khám, ưu tiên")
    @GetMapping("/visits")
    public ApiResponse<VisitStatisticsResponse> getVisitStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) StatisticsGroupBy groupBy) {
        return ApiResponse.success(statisticsService.getVisitStatistics(from, to, groupBy));
    }

    // UC-28: thong ke theo bac si
    @Operation(summary = "UC-28 Thống kê số lượt khám và số bệnh nhân của từng bác sĩ trong khoảng ngày")
    @GetMapping("/doctors")
    public ApiResponse<DoctorStatisticsResponse> getDoctorStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getDoctorStatistics(from, to));
    }

    // UC-29: thong ke khoa / phong
    @Operation(summary = "UC-29 Thống kê khối lượng công việc của từng khoa và từng phòng trong khoảng ngày")
    @GetMapping("/departments")
    public ApiResponse<DepartmentStatisticsResponse> getDepartmentStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getDepartmentStatistics(from, to));
    }

    // UC-30: thong ke dich vu CLS
    @Operation(summary = "UC-30 Thống kê dịch vụ cận lâm sàng đã thực hiện theo loại dịch vụ và theo phòng")
    @GetMapping("/paraclinical")
    public ApiResponse<ParaclinicalStatisticsResponse> getParaclinicalStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getParaclinicalStatistics(from, to));
    }

    // UC-31: thong ke doanh thu
    @Operation(summary = "UC-31 Thống kê doanh thu đã thu, tách phần BHYT chi trả và phần bệnh nhân trả")
    @GetMapping("/revenue")
    public ApiResponse<RevenueStatisticsResponse> getRevenueStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) StatisticsGroupBy groupBy) {
        return ApiResponse.success(statisticsService.getRevenueStatistics(from, to, groupBy));
    }

    // UC-32: benh thuong gap
    @Operation(summary = "UC-32 Thống kê các bệnh thường gặp theo mã ICD-10 trong khoảng ngày")
    @GetMapping("/diagnoses")
    public ApiResponse<DiagnosisStatisticsResponse> getDiagnosisStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(statisticsService.getDiagnosisStatistics(from, to, limit));
    }

    // UC-33: xuat file Excel
    @Operation(summary = "UC-33 Xuất báo cáo thống kê ra tệp Excel (.xlsx); mặc định xuất mọi báo cáo")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportStatistics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Set<StatisticsReport> reports,
            @RequestParam(required = false) StatisticsGroupBy groupBy,
            @RequestParam(required = false) Integer limit) {
        StatisticsExportService.ExportedFile file =
                statisticsExportService.exportExcel(StaffPrincipal.from(jwt), from, to, reports, groupBy, limit);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }
}
