package clinic.backend.controller;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ErrorResponse;
import clinic.backend.dto.response.SessionResponse;
import clinic.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-01 Login with supporting session refresh, and UC-02 Logout (Report 3).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login session lifecycle")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "UC-01 Log in",
            description = "Authenticates a staff account, creates a session, returns a 15-minute access token, "
                    + "and sets the rotating refresh token as an HttpOnly cookie.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Login succeeded.", useReturnTypeSchema = true,
                    headers = @Header(name = "Set-Cookie",
                            description = "HttpOnly refresh-token cookie; include credentials on refresh/logout.",
                            schema = @Schema(type = "string"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "REQUEST_VALIDATION_FAILED or MALFORMED_REQUEST.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "INVALID_CREDENTIALS, ACCOUNT_LOCKED or ACCOUNT_TEMPORARILY_BLOCKED.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "INTERNAL_SERVER_ERROR.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ApiResponse<SessionResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.login(request, response));
    }

    @Operation(
            summary = "Refresh the current session",
            description = "Consumes and rotates the HttpOnly refresh-token cookie and returns a new access token.")
    @Parameters({
            @Parameter(name = "clinic_refresh", in = ParameterIn.COOKIE, required = true,
                    description = "HttpOnly refresh token. The cookie name is configurable; this is the default."),
            @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true,
                    description = "Must exactly match the configured frontend origin.",
                    example = "http://localhost:5173")
    })
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Refresh succeeded; cookie rotated.",
                    useReturnTypeSchema = true,
                    headers = @Header(name = "Set-Cookie", description = "Rotated HttpOnly refresh-token cookie.",
                            schema = @Schema(type = "string"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED: cookie missing, invalid, expired, revoked or reused.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "FORBIDDEN: Origin is missing or not allowed.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "INTERNAL_SERVER_ERROR.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ApiResponse<SessionResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.refresh(request, response));
    }

    @Operation(
            summary = "UC-02 Log out",
            description = "Revokes the session represented by the refresh-token cookie. The operation is idempotent "
                    + "and always clears the browser cookie when Origin is allowed.")
    @Parameters({
            @Parameter(name = "clinic_refresh", in = ParameterIn.COOKIE,
                    description = "HttpOnly refresh token. Missing/already-ended sessions are accepted."),
            @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true,
                    description = "Must exactly match the configured frontend origin.",
                    example = "http://localhost:5173")
    })
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "Session ended or was already absent; no response body.",
                    headers = @Header(name = "Set-Cookie", description = "Clears the refresh-token cookie.",
                            schema = @Schema(type = "string"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "FORBIDDEN: Origin is missing or not allowed.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "INTERNAL_SERVER_ERROR.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
