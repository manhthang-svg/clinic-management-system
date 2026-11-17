package clinic.backend.controller;

import java.time.LocalDate;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ExaminedPatientListResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ExaminedPatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// UC-34: API benh nhan da kham (chi bac si, luon la bac si dang dang nhap)
@RestController
@RequestMapping("/api/v1/examined-patients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
@Tag(name = "Examined patients", description = "Danh sách bệnh nhân bác sĩ đã khám (UC-34)")
public class ExaminedPatientController {

    private final ExaminedPatientService examinedPatientService;

    @Operation(summary = "UC-34 Danh sách lượt khám bác sĩ đang đăng nhập đã khám trong khoảng ngày")
    @GetMapping
    public ApiResponse<ExaminedPatientListResponse> getExaminedPatients(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(examinedPatientService.getExaminedPatients(
                StaffPrincipal.from(jwt), from, to, keyword, page, size));
    }
}
