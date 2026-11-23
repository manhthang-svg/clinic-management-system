package clinic.backend.controller;

import java.time.LocalDate;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RecordArchiveResponse;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.service.RecordArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for EMR Record Archives (UC-79: View Record Archive / BR-120 / BR-43).
 * Intended for Admin/EMR Archivist. Exposes archive metadata without clinical diagnosis content.
 */
@RestController
@RequestMapping("/api/v1/record-archives")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Record Archives", description = "Quản lý lưu trữ hồ sơ bệnh án EMR (UC-79 / BR-120)")
public class RecordArchiveController {

    private final RecordArchiveService recordArchiveService;

    @Operation(summary = "UC-79: Tìm kiếm và xem danh sách lưu trữ hồ sơ bệnh án điện tử")
    @GetMapping
    public ApiResponse<PageResponse<RecordArchiveResponse>> searchArchives(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RecordArchiveRetentionCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(recordArchiveService.searchArchives(keyword, category, from, to, page, size));
    }
}

