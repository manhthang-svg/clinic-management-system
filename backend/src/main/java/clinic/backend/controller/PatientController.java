package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.dto.response.FollowUpAppointmentResponse;
import clinic.backend.dto.response.PatientOverviewResponse;
import clinic.backend.enums.PatientSearchType;
import clinic.backend.security.user.StaffPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import clinic.backend.service.FollowUpAppointmentService;
import clinic.backend.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Quản lý bệnh nhân", description = "Tiếp nhận và tra cứu hồ sơ bệnh nhân")
public class PatientController {

    private final PatientService patientService;
    private final FollowUpAppointmentService followUpAppointmentService;

    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "UC-37 Tạo hồ sơ bệnh nhân")
    public ApiResponse<PatientResponse> createPatient(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePatientRequest request
    ) {
        // The creator comes from the token, never from the request body (same as UC-36 and UC-38).
        return ApiResponse.success(patientService.createPatient(StaffPrincipal.from(jwt).accountId(), request));
    }

    @GetMapping("/possible-duplicates")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "UC-37 AF-02 Hồ sơ có thể trùng (cùng họ tên và ngày sinh)")
    public ApiResponse<List<PatientResponse>> findPossibleDuplicates(
            @RequestParam(required = false) String hoten,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngaysinh
    ) {
        return ApiResponse.success(patientService.findPossibleDuplicates(hoten, ngaysinh));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "UC-38 Cập nhật thông tin bệnh nhân")
    public ApiResponse<PatientResponse> updatePatient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request
    ) {
        return ApiResponse.success(patientService.updatePatient(StaffPrincipal.from(jwt).accountId(), id, request));
    }

    @GetMapping
    // ASSUMPTION: Permission Matrix 4.4 gives the Doctor full search; Technician and Cashier scopes are TBD.
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "UC-35 Tìm kiếm và xem danh sách bệnh nhân (20 bệnh nhân mỗi trang)")
    public ApiResponse<Page<PatientResponse>> listPatients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PatientSearchType type,
            Pageable pageable
    ) {
        return ApiResponse.success(patientService.listPatients(keyword, type, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Hồ sơ hành chính của bệnh nhân (dùng cho UC-38, UC-40)")
    public ApiResponse<PatientResponse> getPatientById(@PathVariable UUID id) {
        return ApiResponse.success(patientService.getPatientById(id));
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "UC-36 Xem thông tin bệnh nhân và lịch sử khám (ghi audit)")
    public ApiResponse<PatientOverviewResponse> getPatientOverview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return ApiResponse.success(patientService.getPatientOverview(StaffPrincipal.from(jwt).accountId(), id));
    }

    @GetMapping("/{id}/follow-up-appointment")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<ApiResponse<FollowUpAppointmentResponse>> getFollowUpAppointment(@PathVariable UUID id) {
        FollowUpAppointmentResponse appointment = followUpAppointmentService.findScheduledForPatient(id);
        return appointment == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(ApiResponse.success(appointment));
    }
}
