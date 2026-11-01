package clinic.backend.controller;

import clinic.backend.dto.request.InsuranceVerificationRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.InsuranceVerificationResponse;
import clinic.backend.service.InsuranceVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/insurance/verifications")
public class InsuranceVerificationController {

    private final InsuranceVerificationService service;

    @PostMapping
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ApiResponse<InsuranceVerificationResponse> verify(
            @Valid @RequestBody InsuranceVerificationRequest request) {
        return ApiResponse.success(service.verify(request));
    }
}
