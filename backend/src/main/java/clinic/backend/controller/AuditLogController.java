package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.AuditLogDetailResponse;
import clinic.backend.dto.response.AuditLogItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.service.AuditLogViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-78 View Audit Log: the Admin reads the business audit trail. Nothing here writes or deletes. */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Audit Logs", description = "Xem nhật ký hệ thống (UC-78)")
public class AuditLogController {

    private final AuditLogViewService auditLogViewService;

    @Operation(summary = "UC-78: Tìm kiếm nhật ký hệ thống, mới nhất trước")
    @GetMapping
    public ApiResponse<PageResponse<AuditLogItemResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AuditLogAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.success(
                auditLogViewService.search(keyword, action, entityType, outcome, from, to, page, size));
    }

    @Operation(summary = "UC-78: Danh sách loại đối tượng đã có nhật ký (cho bộ lọc)")
    @GetMapping("/entity-types")
    public ApiResponse<List<String>> entityTypes() {
        return ApiResponse.success(auditLogViewService.listEntityTypes());
    }

    @Operation(summary = "UC-78: Chi tiết một dòng nhật ký, gồm giá trị cũ và mới")
    @GetMapping("/{id}")
    public ApiResponse<AuditLogDetailResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(auditLogViewService.getDetail(id));
    }
}
