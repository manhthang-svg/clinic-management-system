package clinic.backend.controller;

import java.net.URI;

import clinic.backend.dto.LoginRequest;
import clinic.backend.dto.SessionResponse;
import clinic.backend.exception.ApiErrorCode;
import clinic.backend.exception.ApiProblemFactory;
import clinic.backend.security.RequestOriginVerifier;
import clinic.backend.service.AuthenticationResult;
import clinic.backend.service.LoginService;
import clinic.backend.service.LogoutService;
import clinic.backend.service.RefreshSessionService;
import clinic.backend.service.SessionTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class AuthController {

    private final LoginService loginService;
    private final RefreshSessionService refreshSessionService;
    private final LogoutService logoutService;
    private final RefreshTokenCookies cookies;
    private final RequestOriginVerifier originVerifier;
    private final ApiProblemFactory problemFactory;

    AuthController(
            LoginService loginService,
            RefreshSessionService refreshSessionService,
            LogoutService logoutService,
            RefreshTokenCookies cookies,
            RequestOriginVerifier originVerifier,
            ApiProblemFactory problemFactory) {

        this.loginService = loginService;
        this.refreshSessionService = refreshSessionService;
        this.logoutService = logoutService;
        this.cookies = cookies;
        this.originVerifier = originVerifier;
        this.problemFactory = problemFactory;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthenticationResult result = loginService.login(request.username(), request.password());
        return switch (result) {
            case AuthenticationResult.Authenticated authenticated -> sessionResponse(authenticated.tokens());
            case AuthenticationResult.Rejected rejected -> unauthorized(
                    ApiErrorCode.INVALID_CREDENTIALS, "Invalid username or password.", httpRequest);
        };
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(HttpServletRequest httpRequest) {
        if (!originVerifier.isAllowed(httpRequest)) {
            return forbiddenOrigin(httpRequest);
        }

        AuthenticationResult result = refreshSessionService.refresh(cookies.read(httpRequest).orElse(null));
        return switch (result) {
            case AuthenticationResult.Authenticated authenticated -> sessionResponse(authenticated.tokens());
            case AuthenticationResult.Rejected rejected -> ResponseEntity
                    .status(ApiErrorCode.UNAUTHORIZED.status())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problemFactory.create(
                            ApiErrorCode.UNAUTHORIZED,
                            "The session is no longer valid. Log in again.",
                            URI.create(httpRequest.getRequestURI())));
        };
    }

    @PostMapping("/logout")
    ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        if (!originVerifier.isAllowed(httpRequest)) {
            return forbiddenOrigin(httpRequest);
        }

        logoutService.logout(cookies.read(httpRequest).orElse(null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<SessionResponse> sessionResponse(SessionTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.issue(tokens.refreshToken(), tokens.sessionExpiresAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(SessionResponse.from(tokens));
    }

    private ResponseEntity<?> unauthorized(ApiErrorCode code, String detail, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemFactory.create(code, detail, URI.create(request.getRequestURI())));
    }

    private ResponseEntity<?> forbiddenOrigin(HttpServletRequest request) {
        return ResponseEntity.status(ApiErrorCode.FORBIDDEN.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemFactory.create(
                        ApiErrorCode.FORBIDDEN,
                        "The request origin is not allowed.",
                        URI.create(request.getRequestURI())));
    }
}
