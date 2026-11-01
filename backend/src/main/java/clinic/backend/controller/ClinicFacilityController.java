package clinic.backend.controller;

import clinic.backend.dto.request.UpdateClinicFacilityRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ClinicFacilityResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ClinicFacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clinic-facility")
@Tag(name = "Clinic facility", description = "Admin-only UC-77")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class ClinicFacilityController {

    private final ClinicFacilityService service;

    @Operation(summary = "UC-77 View the clinic facility information",
            description = "Returns configured=false until the facility is declared. "
                    + "maCskcbLocked is true once a visit exists.")
    @GetMapping
    public ApiResponse<ClinicFacilityResponse> get() {
        return ApiResponse.success(service.getFacility());
    }

    @Operation(summary = "UC-77 Declare or update the clinic facility information",
            description = "Send version=null for the first declaration. Returns 409 FACILITY_CODE_LOCKED when "
                    + "the facility code changes after the first visit, and 409 CONCURRENT_MODIFICATION when "
                    + "the version is stale.")
    @PutMapping
    public ApiResponse<ClinicFacilityResponse> save(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateClinicFacilityRequest request) {
        return ApiResponse.success(service.saveFacility(StaffPrincipal.from(jwt), request));
    }
}
