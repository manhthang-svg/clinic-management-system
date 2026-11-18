package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordRequest;
import clinic.backend.dto.request.ExportMedicalRecordDataRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ExportMedicalRecordDataResponse;
import jakarta.validation.Valid;
import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordItemResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitItemResponse;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.MedicalRecordExportService;
import clinic.backend.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/**
 * Controller for Medical Records (Screen #39 / UC-51: Medical Record History, UC-52: Medical Record Detail, UC-53: Export).
 */
@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
// BR-120: Admin exports XML/JSON but does not see clinical content on screen, so only the Doctor reads records.
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Medical Records", description = "Hồ sơ bệnh án / Lịch sử hồ sơ bệnh án (Screen #39, UC-51, UC-52)")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;
    private final MedicalRecordExportService medicalRecordExportService;

    @Operation(summary = "Screen #39 Cấp 1: Tìm kiếm danh sách bệnh nhân khớp điều kiện lọc")
    @GetMapping("/patients")
    public ApiResponse<PageResponse<MedicalRecordPatientItemResponse>> searchPatients(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) String icd10,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorAccountId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(medicalRecordService.searchPatients(from, to, status, roomId, icd10, keyword, page, size, actorAccountId));
    }

    @Operation(summary = "Screen #39 Cấp 2 / AF-01: Xem danh sách lượt khám của một bệnh nhân")
    @GetMapping("/patients/{patientId}/visits")
    public ApiResponse<List<PatientVisitItemResponse>> getPatientVisits(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorAccountId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(medicalRecordService.getPatientVisits(patientId, actorAccountId));
    }

    @Operation(summary = "UC-51: Tìm kiếm và xem danh sách hồ sơ bệnh án (dạng phẳng)")
    @GetMapping
    public ApiResponse<PageResponse<MedicalRecordItemResponse>> getMedicalRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MedicalRecordStatus status,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(medicalRecordService.getMedicalRecords(from, to, status, roomId, keyword, page, size));
    }

    @Operation(summary = "UC-52: Xem chi tiết hồ sơ bệnh án theo ID")
    @GetMapping("/{id}")
    public ApiResponse<MedicalRecordDetailResponse> getMedicalRecordDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorAccountId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(medicalRecordService.getMedicalRecordDetail(id, actorAccountId));
    }

    @Operation(summary = "UC-52: Xem chi tiết hồ sơ bệnh án theo lượt khám (visitId)")
    @GetMapping("/by-visit/{visitId}")
    public ApiResponse<MedicalRecordDetailResponse> getMedicalRecordByVisitId(
            @PathVariable UUID visitId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorAccountId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        return ApiResponse.success(medicalRecordService.getMedicalRecordByVisitId(visitId, actorAccountId));
    }

    @Operation(summary = "UC-53: Xuất hồ sơ bệnh án đã đóng ra PDF theo mẫu 15/BV1 (TT 32/2023)")
    @PostMapping("/{id}/commands/export-pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID id,
            @RequestBody ExportMedicalRecordRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        MedicalRecordExportService.ExportedFile file =
                medicalRecordExportService.exportPdf(StaffPrincipal.from(jwt), id, request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @Operation(summary = "BR-42, I.3.3: Ghi nhận nhật ký truy cập/tải tệp kết quả cận lâm sàng")
    @PostMapping("/paraclinical-files/{imageId}/access-log")
    public ApiResponse<Void> recordFileAccess(
            @PathVariable UUID imageId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorAccountId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        medicalRecordService.recordFileAccess(imageId, actorAccountId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "UC-80 / BR-43: Kết xuất dữ liệu hồ sơ bệnh án điện tử (XML / JSON) theo CV 365")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @PostMapping("/{id}/export-data")
    public ResponseEntity<byte[]> exportMedicalRecordData(
            @PathVariable UUID id,
            @RequestBody @Valid ExportMedicalRecordDataRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorAccountId = jwt != null ? StaffPrincipal.from(jwt).accountId() : null;
        ExportMedicalRecordDataResponse response = medicalRecordService.exportMedicalRecordData(id, request, actorAccountId);

        String safeFileName = response.fileName() != null
                ? response.fileName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "HoSoBenhAn.dat";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFileName + "\"")
                .header("X-File-SHA256", response.fileSha256())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, X-File-SHA256")
                .contentType(MediaType.parseMediaType(response.contentType()))
                .body(response.content());
    }
}
