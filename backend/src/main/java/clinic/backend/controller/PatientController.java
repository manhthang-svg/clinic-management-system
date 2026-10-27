package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PatientResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Quản lý bệnh nhân", description = "Tiếp nhận và tra cứu hồ sơ bệnh nhân")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "UC-37 Tạo hồ sơ bệnh nhân")
    public ApiResponse<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request
    ) {
        return ApiResponse.success(patientService.createPatient(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "UC-38 Cập nhật thông tin bệnh nhân")
    public ApiResponse<PatientResponse> updatePatient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request
    ) {
        return ApiResponse.success(patientService.updatePatient(id, request));
    }

    @GetMapping
    @Operation(summary = "UC-35 Tìm kiếm và xem danh sách bệnh nhân")
    public ApiResponse<Page<PatientResponse>> listPatients(
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return ApiResponse.success(patientService.listPatients(keyword, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "UC-36 Xem chi tiết hồ sơ bệnh nhân")
    public ApiResponse<PatientResponse> getPatientById(@PathVariable UUID id) {
        return ApiResponse.success(patientService.getPatientById(id));
    }
}
