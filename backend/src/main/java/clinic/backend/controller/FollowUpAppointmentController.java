package clinic.backend.controller;

import java.util.UUID;

import clinic.backend.dto.request.CancelFollowUpAppointmentRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.FollowUpAppointmentResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.FollowUpAppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/follow-up-appointments")
public class FollowUpAppointmentController {

    private final FollowUpAppointmentService service;

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ApiResponse<FollowUpAppointmentResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CancelFollowUpAppointmentRequest request) {
        return ApiResponse.success(service.cancel(StaffPrincipal.from(jwt), appointmentId, request));
    }
}
