package clinic.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import clinic.backend.dto.request.CreateAccountRequest;
import clinic.backend.dto.request.AssignRoomRequest;
import clinic.backend.dto.request.LockAccountRequest;
import clinic.backend.dto.request.NationalPrescriberRequest;
import clinic.backend.dto.request.ResetAccountPasswordRequest;
import clinic.backend.dto.request.RoleChangeRequest;
import clinic.backend.dto.request.UnlockAccountRequest;
import clinic.backend.dto.request.UpdateAccountRequest;
import clinic.backend.dto.response.AccountActivityResponse;
import clinic.backend.dto.response.AccountDetailResponse;
import clinic.backend.dto.response.AccountHistoryResponse;
import clinic.backend.dto.response.AccountSummaryResponse;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.CreatedAccountResponse;
import clinic.backend.dto.response.DepartmentOptionResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RoleChangeCheckResponse;
import clinic.backend.dto.response.TemporaryPasswordResponse;
import clinic.backend.dto.response.WorkInProgressResponse;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AccountAdministrationService;
import clinic.backend.service.NationalPrescriberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts")
@Tag(name = "Account administration", description = "Admin-only UC-05 through UC-11")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class AccountAdministrationController {

    private final AccountAdministrationService service;
    private final NationalPrescriberService nationalPrescriberService;

    @Operation(summary = "UC-05 List department options",
            description = "Returns departments used by the account filters and create/update forms.")
    @GetMapping("/departments")
    public ApiResponse<List<DepartmentOptionResponse>> departments() {
        return ApiResponse.success(service.listDepartments());
    }

    @Operation(summary = "UC-05 View and search staff accounts",
            description = "Returns a zero-based page filtered by search text, role, department and status.")
    @GetMapping
    public ApiResponse<PageResponse<AccountSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc")
            @Pattern(regexp = "(?:username|fullName|role|status|createdAt|lastLoginAt),(?:asc|desc)") String sort) {
        String[] parts = sort.split(",", -1);
        String field = parts[0];
        String direction = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "asc";
        boolean descending = "desc".equals(direction);
        return ApiResponse.success(service.listAccounts(search, role, departmentId, status, page, size, field, descending));
    }

    @Operation(summary = "UC-06 View an account",
            description = "Returns account details together with the actions currently allowed for that account.")
    @GetMapping("/{accountId}")
    public ApiResponse<AccountDetailResponse> detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return ApiResponse.success(service.getAccount(StaffPrincipal.from(jwt), accountId));
    }

    @Operation(summary = "UC-07 Create a staff account",
            description = "Creates an account and returns its temporary password exactly once.")
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedAccountResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        CreatedAccountResponse created = service.createAccount(StaffPrincipal.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.accountId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "UC-08 Update a staff account",
            description = "Updates editable account fields and writes one history entry for each changed field.")
    @PutMapping("/{accountId}")
    public ApiResponse<AccountDetailResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ApiResponse.success(service.updateAccount(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-09 Check a role change",
            description = "Checks whether unfinished work prevents the requested role change.")
    @PostMapping("/{accountId}/commands/check-role-change")
    public ApiResponse<RoleChangeCheckResponse> checkRoleChange(
            @PathVariable UUID accountId, @Valid @RequestBody RoleChangeRequest request) {
        return ApiResponse.success(service.checkRoleChange(accountId, request));
    }

    @Operation(summary = "UC-09 View unfinished work",
            description = "Returns active visit and order counts relevant to a role change.")
    @GetMapping("/{accountId}/work-in-progress")
    public ApiResponse<WorkInProgressResponse> workInProgress(@PathVariable UUID accountId) {
        return ApiResponse.success(service.workInProgress(accountId));
    }

    @Operation(summary = "UC-09 Change account role",
            description = "Changes the role only when the account has no blocking unfinished work.")
    @PostMapping("/{accountId}/commands/change-role")
    public ApiResponse<AccountDetailResponse> changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody RoleChangeRequest request) {
        return ApiResponse.success(service.changeRole(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-15 Assign a staff account to a room")
    @PostMapping("/{accountId}/commands/assign-room")
    public ApiResponse<AccountDetailResponse> assignRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody AssignRoomRequest request) {
        return ApiResponse.success(service.assignRoom(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-10 Lock an account",
            description = "Manually locks an active account and records the supplied reason.")
    @PostMapping("/{accountId}/commands/lock")
    public ApiResponse<AccountDetailResponse> lock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody LockAccountRequest request) {
        return ApiResponse.success(service.lock(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-10 Unlock an account",
            description = "Manually unlocks a locked account and records the supplied reason.")
    @PostMapping("/{accountId}/commands/unlock")
    public ApiResponse<AccountDetailResponse> unlock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody UnlockAccountRequest request) {
        return ApiResponse.success(service.unlock(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-11 Reset an account password",
            description = "Invalidates existing sessions and returns a new temporary password exactly once.")
    @PostMapping("/{accountId}/commands/reset-password")
    public ApiResponse<TemporaryPasswordResponse> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody ResetAccountPasswordRequest request) {
        return ApiResponse.success(service.resetPassword(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-06 View recent account activity",
            description = "Returns the most recent security events for the selected account.")
    @GetMapping("/{accountId}/activities")
    public ApiResponse<List<AccountActivityResponse>> activities(
            @PathVariable UUID accountId, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(service.recentActivities(accountId, limit));
    }

    @Operation(summary = "UC-06 View account change history",
            description = "Returns a zero-based page of field-level account changes.")
    @GetMapping("/{accountId}/change-history")
    public ApiResponse<PageResponse<AccountHistoryResponse>> history(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.changeHistory(accountId, page, size));
    }

    @Operation(summary = "UC-93 Add the Doctor to the facility on the national prescription system",
            description = "QĐ 808 IV. Signs the facility in (III) when no token is kept; the facility password is "
                    + "sent only when the server asks for it and is never stored.")
    @PostMapping("/{accountId}/commands/add-to-national-prescription-system")
    public ApiResponse<AccountDetailResponse> addToNationalPrescriptionSystem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @RequestBody NationalPrescriberRequest request) {
        return ApiResponse.success(
                nationalPrescriberService.addToFacility(StaffPrincipal.from(jwt), accountId, request));
    }

    @Operation(summary = "UC-93 Remove the Doctor from the facility on the national prescription system",
            description = "QĐ 808 V. Signs the facility in (III) when no token is kept.")
    @PostMapping("/{accountId}/commands/remove-from-national-prescription-system")
    public ApiResponse<AccountDetailResponse> removeFromNationalPrescriptionSystem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @RequestBody NationalPrescriberRequest request) {
        return ApiResponse.success(
                nationalPrescriberService.removeFromFacility(StaffPrincipal.from(jwt), accountId, request));
    }
}
