package clinic.backend.service;

import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.CreateAccountRequest;
import clinic.backend.dto.request.AssignRoomRequest;
import clinic.backend.dto.request.LockAccountRequest;
import clinic.backend.dto.request.ResetAccountPasswordRequest;
import clinic.backend.dto.request.RoleChangeRequest;
import clinic.backend.dto.request.UnlockAccountRequest;
import clinic.backend.dto.request.UpdateAccountRequest;
import clinic.backend.dto.response.AccountActivityResponse;
import clinic.backend.dto.response.AccountDetailResponse;
import clinic.backend.dto.response.AccountHistoryResponse;
import clinic.backend.dto.response.AccountSummaryResponse;
import clinic.backend.dto.response.CreatedAccountResponse;
import clinic.backend.dto.response.DepartmentOptionResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RoleChangeCheckResponse;
import clinic.backend.dto.response.TemporaryPasswordResponse;
import clinic.backend.dto.response.WorkInProgressResponse;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.security.user.StaffPrincipal;

public interface AccountAdministrationService {

    List<DepartmentOptionResponse> listDepartments();

    PageResponse<AccountSummaryResponse> listAccounts(
            String search, Role role, UUID departmentId, AccountStatus status,
            int page, int size, String sortField, boolean descending);

    AccountDetailResponse getAccount(StaffPrincipal principal, UUID accountId);

    CreatedAccountResponse createAccount(StaffPrincipal principal, CreateAccountRequest request);

    AccountDetailResponse updateAccount(StaffPrincipal principal, UUID accountId, UpdateAccountRequest request);

    RoleChangeCheckResponse checkRoleChange(UUID accountId, RoleChangeRequest request);

    WorkInProgressResponse workInProgress(UUID accountId);

    AccountDetailResponse changeRole(StaffPrincipal principal, UUID accountId, RoleChangeRequest request);

    AccountDetailResponse assignRoom(StaffPrincipal principal, UUID accountId, AssignRoomRequest request);

    AccountDetailResponse lock(StaffPrincipal principal, UUID accountId, LockAccountRequest request);

    AccountDetailResponse unlock(StaffPrincipal principal, UUID accountId, UnlockAccountRequest request);

    TemporaryPasswordResponse resetPassword(
            StaffPrincipal principal, UUID accountId, ResetAccountPasswordRequest request);

    List<AccountActivityResponse> recentActivities(UUID accountId, int limit);

    PageResponse<AccountHistoryResponse> changeHistory(UUID accountId, int page, int size);
}
