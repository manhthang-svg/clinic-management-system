package clinic.backend.controller;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.SessionResponse;
import clinic.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
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
 * UC-1 Login with the supporting session refresh, and UC-2 Logout (docs/use-cases/).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login session lifecycle")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Log in")
    @PostMapping("/login")
    public ApiResponse<SessionResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.login(request, response));
    }

    @Operation(summary = "Rotate the refresh-token cookie and issue a new access token")
    @PostMapping("/refresh")
    public ApiResponse<SessionResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.refresh(request, response));
    }

    @Operation(summary = "Log out of the current session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
