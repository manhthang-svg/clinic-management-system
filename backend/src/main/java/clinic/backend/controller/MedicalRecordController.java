package clinic.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordRequest;
import clinic.backend.dto.request.ExportMedicalRecordDataRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ExportMedicalRecordDataResponse;
import jakarta.validation.Valid;
import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitHistoryResponse;
import clinic.backend.dto.response.ResultFileResponse;
import clinic.backend.enums.MedicalRecordVisitFilter;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.MedicalRecordDataExportService;
import clinic.backend.service.MedicalRecordExportService;
import clinic.backend.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
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
 * UC-51 Medical Record List, UC-52 Medical Record Detail, UC-53 Export (docs/uc-51-52-medical-record-view.md).
 */
@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
// BR-120: Admin exports XML/JSON but does not see clinical content on screen, so only the Doctor reads records.
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Medical Records", description = "Hồ sơ bệnh án (UC-51, UC-52, UC-53)")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;
    private final MedicalRecordExportService medicalRecordExportService;
    private final MedicalRecordDataExportService medicalRecordDataExportService;

    @Operation(summary = "UC-51 cấp 1: tìm bệnh nhân theo từ khóa và bộ lọc lượt khám")
    @GetMapping("/patients")
    public ApiResponse<PageResponse<MedicalRecordPatientItemResponse>> searchPatients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) String icd10,
            @RequestParam(required = false) MedicalRecordVisitFilter status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(medicalRecordService.searchPatients(
                keyword, from, to, roomId, icd10, status, page, size, actor(jwt)));
    }

    @Operation(summary = "UC-51 cấp 2 / AF-01: bệnh nhân và danh sách lượt khám")
    @GetMapping("/patients/{patientId}/visits")
    public ApiResponse<PatientVisitHistoryResponse> getPatientVisits(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(medicalRecordService.getPatientVisits(patientId, actor(jwt)));
    }

    @Operation(summary = "UC-52: chi tiết hồ sơ bệnh án theo ID hồ sơ")
    @GetMapping("/{id}")
    public ApiResponse<MedicalRecordDetailResponse> getMedicalRecordDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(medicalRecordService.getMedicalRecordDetail(id, actor(jwt)));
    }

    @Operation(summary = "UC-52: chi tiết hồ sơ bệnh án theo lượt khám")
    @GetMapping("/by-visit/{visitId}")
    public ApiResponse<MedicalRecordDetailResponse> getMedicalRecordByVisitId(
            @PathVariable UUID visitId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(medicalRecordService.getMedicalRecordByVisitId(visitId, actor(jwt)));
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

    @Operation(summary = "UC-52 bước 4: tải tệp kết quả CLS sau khi kiểm tra quyền (BR-52-06)")
    @GetMapping("/{medicalRecordId}/result-files/{fileId}")
    public ResponseEntity<byte[]> downloadResultFile(
            @PathVariable UUID medicalRecordId,
            @PathVariable UUID fileId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ResultFileResponse file = medicalRecordService.downloadResultFile(medicalRecordId, fileId, actor(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }

    @Operation(summary = "UC-80 / BR-43: Kết xuất dữ liệu hồ sơ bệnh án điện tử (XML / JSON) theo CV 365")
    @PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
    @PostMapping("/{id}/export-data")
    public ResponseEntity<byte[]> exportMedicalRecordData(
            @PathVariable UUID id,
            @RequestBody @Valid ExportMedicalRecordDataRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ExportMedicalRecordDataResponse response =
                medicalRecordDataExportService.exportMedicalRecordData(id, request, actor(jwt));

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

    private static UUID actor(Jwt jwt) {
        return StaffPrincipal.from(jwt).accountId();
    }
}
