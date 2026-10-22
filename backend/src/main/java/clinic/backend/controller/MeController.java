package clinic.backend.controller;

import clinic.backend.dto.request.ChangePasswordRequest;
import clinic.backend.dto.request.UpdateProfileRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ErrorResponse;
import clinic.backend.dto.response.ProfileResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.StaffAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @Operation(summary = "UC-04 View own profile",
            description = "Returns only the authenticated account's profile. Allowed while a password change is required.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Profile returned.", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED: bearer token missing/invalid or account no longer active.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "INTERNAL_SERVER_ERROR.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(staffAccountService.getOwnProfile(StaffPrincipal.from(jwt)));
    }

    @Operation(summary = "UC-04 Update own contact information",
            description = "Replaces the caller's complete editable contact representation (phone and email). "
                    + "A session that still requires a password change may view, but may not update, the profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Profile updated, or returned unchanged for an exact no-op.",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "REQUEST_VALIDATION_FAILED or MALFORMED_REQUEST.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED: bearer token missing/invalid or account no longer active.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "FORBIDDEN: password must be changed before updating the profile.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "EMAIL_ALREADY_EXISTS or CONCURRENT_MODIFICATION.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "INTERNAL_SERVER_ERROR; no profile change is committed.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(staffAccountService.updateOwnProfile(StaffPrincipal.from(jwt), request));
    }

    @Operation(summary = "UC-03 Change own password",
            description = "Changes the caller's password, clears passwordChangeRequired, revokes every other session, "
                    + "and keeps the current session active. Refresh afterward to obtain an unrestricted access token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "Password changed; no response body."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "REQUEST_VALIDATION_FAILED or MALFORMED_REQUEST.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED: bearer token missing/invalid or account locked.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "CURRENT_PASSWORD_INCORRECT or PASSWORD_POLICY_VIOLATION.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "INTERNAL_SERVER_ERROR; old password remains valid.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/commands/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        staffAccountService.changePassword(StaffPrincipal.from(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
