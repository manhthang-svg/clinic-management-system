package clinic.backend.controller;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.StaffAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

/**
 * Self-service operations on the caller's own account. The account always comes from the token.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "My account", description = "Self-service operations on the caller's own account")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class MeController {

    private final StaffAccountService staffAccountService;

    @Operation(summary = "UC-3 Change password")
    @PostMapping("/commands/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        staffAccountService.changePassword(StaffPrincipal.from(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
