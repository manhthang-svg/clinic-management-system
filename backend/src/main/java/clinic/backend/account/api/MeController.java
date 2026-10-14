package clinic.backend.account.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import clinic.backend.account.application.ChangePasswordResult;
import clinic.backend.account.application.ChangePasswordService;
import clinic.backend.shared.api.error.ApiErrorCode;
import clinic.backend.shared.api.error.ApiFieldError;
import clinic.backend.shared.api.error.ApiProblemFactory;
import clinic.backend.shared.security.AccessTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service operations on the caller's own account. The account always comes from the token.
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final ChangePasswordService changePasswordService;
    private final ApiProblemFactory problemFactory;

    MeController(ChangePasswordService changePasswordService, ApiProblemFactory problemFactory) {
        this.changePasswordService = changePasswordService;
        this.problemFactory = problemFactory;
    }

    /**
     * UC-3 Change Password.
     */
    @PostMapping("/commands/change-password")
    ResponseEntity<?> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        ChangePasswordResult result = changePasswordService.changePassword(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(AccessTokenClaims.SESSION_ID)),
                request.currentPassword(),
                request.newPassword());

        URI instance = URI.create(httpRequest.getRequestURI());
        return switch (result) {
            case ChangePasswordResult.Changed changed -> ResponseEntity.noContent().build();
            case ChangePasswordResult.CurrentPasswordIncorrect incorrect -> problem(problemFactory.create(
                    ApiErrorCode.CURRENT_PASSWORD_INCORRECT,
                    "The current password is incorrect.",
                    instance));
            case ChangePasswordResult.PolicyViolated violated -> problem(problemFactory.create(
                    ApiErrorCode.PASSWORD_POLICY_VIOLATION,
                    "The new password does not satisfy the password policy.",
                    instance,
                    List.of(new ApiFieldError("newPassword", violated.violation().name(), policyMessage(violated)))));
            case ChangePasswordResult.AccountUnavailable unavailable -> ResponseEntity
                    .status(ApiErrorCode.UNAUTHORIZED.status())
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problemFactory.create(
                            ApiErrorCode.UNAUTHORIZED, "Authentication is required to access this resource.", instance));
        };
    }

    private static String policyMessage(ChangePasswordResult.PolicyViolated violated) {
        return switch (violated.violation()) {
            case TOO_SHORT -> "The password must contain at least 8 characters.";
            case TOO_LONG -> "The password must contain at most 64 characters and 72 bytes.";
            case SAME_AS_CURRENT -> "The new password must differ from the current password.";
        };
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
