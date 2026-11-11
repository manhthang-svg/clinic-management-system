import { useEffect, useState, useMemo } from "react";
import {
  Navigate,
  generatePath,
  matchPath,
  useLocation,
  useNavigate,
} from "react-router";
import {
  Users,
  UserCheck,
  Lock,
  KeyRound,
  Plus,
  House,
  ChevronRight,
  CheckCircle2,
} from "lucide-react";
import { AdminLayout } from "../../layouts";
import { ROUTES } from "../../routes/routes";
import { ConfirmModal } from "../../components/ui";
import { toast } from "sonner";
import {
  AccountFilterBar,
  AccountTable,
  AccountDetailView,
  AccountCreateView,
  AccountEditView,
  AccountAssignRoleView,
  AccountResetPasswordModal,
  ProfileView,
  AccountStatCard,
} from "../../components/accounts";
import { percentOf } from "../../components/accounts/accountFormat";
import {
  REASON_LENGTH_MESSAGE,
  accountErrorMessage,
  accountFieldErrors,
  msg47,
} from "../../components/accounts/accountErrors";
import { createAccountApi } from "../../apis/accountApi";
import { apiClient } from "../../services/session";
import {
  PAGE_SIZE_OPTIONS,
  ROLE_LABELS,
} from "../../constants";

const defaultAccountApi = createAccountApi(apiClient);

const MSG46 = "Không thể thực hiện thao tác này đối với tài khoản Admin.";
// BR-97: an action reason has 5–500 characters after trimming.
const LOCK_REASON_MIN = 5;
const LOCK_REASON_MAX = 500;

/** UC-10 lock reason check; returns the message to show under the field, or "". */
function lockReasonMessage(reason) {
  if (!reason) return "Vui lòng nhập lý do."; // MSG31
  return reason.length < LOCK_REASON_MIN ? REASON_LENGTH_MESSAGE : "";
}

function fromApi(account) {
  if (!account) return account;
  return {
    ...account,
    phone: account.phoneNumber ?? account.phone ?? "",
    title: account.academicTitle ?? account.title,
    practiceLicenseNo: account.practiceLicenseNo ?? "",
    maLienthongBacsi: account.maLienthongBacsi ?? "",
    failedLoginCount: account.failedLoginAttempts ?? account.failedLoginCount ?? 0,
  };
}

function isMissingAccountError(error) {
  return error?.status === 404 ||
    ["ACCOUNT_NOT_FOUND", "NOT_FOUND"].includes(error?.errorResponse?.code);
}

function isCanonicalUuid(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

function accountRequest(form) {
  return {
    fullName: form.fullName.trim(),
    academicTitle: form.title.trim() || null,
    practiceLicenseNo: form.practiceLicenseNo.trim() || null,
    dateOfBirth: form.dateOfBirth || null,
    gender: form.gender || null,
    phoneNumber: form.phone.trim(),
    email: form.email.trim() || null,
    address: form.address.trim() || null,
  };
}

const { ADMIN } = ROUTES;

const SEARCH_DEBOUNCE_MS = 300;
const SUMMARY_PAGE_SIZE = 50;
const accountPageCache = new WeakMap();

function updateAccountPageCache(accountApi, values) {
  accountPageCache.set(accountApi, {
    ...accountPageCache.get(accountApi),
    ...values,
  });
}

/** Every account, page by page; feeds the statistics and the client-side duplicate checks. */
async function listAllAccounts(accountApi, signal) {
  const accounts = [];
  for (let page = 0; ; page += 1) {
    const result = await accountApi.list({ page, size: SUMMARY_PAGE_SIZE }, signal);
    accounts.push(...result.content.map(fromApi));
    if (page + 1 >= result.page.totalPages) return accounts;
  }
}

/** Which account screen the URL shows; null for paths this page does not own. */
function viewFromPath(path) {
  if (matchPath(ADMIN.ACCOUNTS, path)) return { name: "list" };
  if (matchPath(ADMIN.ACCOUNT_CREATE, path)) return { name: "create" };
  if (matchPath(ADMIN.PROFILE, path)) return { name: "profile" };
  const assignRole = matchPath(ADMIN.ACCOUNT_ASSIGN_ROLE, path);
  if (assignRole) return { name: "assign-role", id: assignRole.params.id };
  const edit = matchPath(ADMIN.ACCOUNT_EDIT, path);
  if (edit) return { name: "edit", id: edit.params.id };

  const detail = matchPath(ADMIN.ACCOUNT_DETAIL, path);
  if (detail) return { name: "detail", id: detail.params.id };

  return null;
}

export function AccountManagementPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  onNavigate,
  notice,
  accountApi = defaultAccountApi,
}) {
  const cached = accountPageCache.get(accountApi);
  // All accounts (statistics, overview, duplicate checks) and the server-filtered table page.
  const [accounts, setAccounts] = useState(() => cached?.accounts ?? []);
  const [tablePage, setTablePage] = useState(() => cached?.tablePage ?? {
    query: null, content: [], totalElements: 0, error: null,
  });
  const [tableReady, setTableReady] = useState(() => cached?.tablePage?.query != null);
  const [reloadKey, setReloadKey] = useState(0);
  const [details, setDetails] = useState({});
  const [missingIds, setMissingIds] = useState([]);
  const [profile, setProfile] = useState(null);
  const [activities, setActivities] = useState([]);
  const [changeHistory, setChangeHistory] = useState([]);
  const [historyPage, setHistoryPage] = useState({ accountId: null, page: 0, totalPages: 0 });
  const [workInProgress, setWorkInProgress] = useState(null);
  const [departments, setDepartments] = useState(() => cached?.departments ?? []);
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [departmentFilter, setDepartmentFilter] = useState("ALL");
  const [sort, setSort] = useState("createdAt,desc");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
  // The URL decides which account screen is displayed.
  const location = useLocation();
  const navigate = useNavigate();
  const matchedView = viewFromPath(location.pathname);
  const view = matchedView || { name: "list" };

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedSearch(searchQuery.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [searchQuery]);

  useEffect(() => {
    // Let the visible table request finish first. The complete list is only
    // needed for statistics and duplicate checks, so it must not delay first paint.
    if (!tableReady) return undefined;
    const controller = new AbortController();
    listAllAccounts(accountApi, controller.signal)
      .then((items) => {
        setAccounts(items);
        updateAccountPageCache(accountApi, { accounts: items });
      })
      .catch((error) => {
        if (error.name !== "AbortError") toast.error(accountErrorMessage(error));
      });
    return () => controller.abort();
  }, [accountApi, reloadKey, tableReady]);

  useEffect(() => {
    const controller = new AbortController();
    accountApi.departments(controller.signal)
      .then((items) => {
        const activeDepartments = items.filter((item) => item.active);
        setDepartments(activeDepartments);
        updateAccountPageCache(accountApi, { departments: activeDepartments });
      })
      .catch((error) => error.name !== "AbortError" && toast.error(accountErrorMessage(error)));
    return () => controller.abort();
  }, [accountApi]);

  // UC-5: the server searches, filters, and pages the table.
  const tableQuery = JSON.stringify([debouncedSearch, roleFilter, departmentFilter, statusFilter, sort, page, pageSize, reloadKey]);
  const loading = tablePage.query !== tableQuery;
  const loadError = loading ? null : tablePage.error;

  useEffect(() => {
    const controller = new AbortController();
    accountApi.list({
      search: debouncedSearch,
      role: roleFilter === "ALL" ? undefined : roleFilter,
      departmentId: departmentFilter === "ALL" ? undefined : departmentFilter,
      status: statusFilter === "ALL" ? undefined : statusFilter,
      sort,
      page: page - 1,
      size: pageSize,
    }, controller.signal)
      .then((result) => {
        const nextPage = {
          query: tableQuery,
          content: result.content.map(fromApi),
          totalElements: result.page.totalElements,
          error: null,
        };
        setTablePage(nextPage);
        setTableReady(true);
        updateAccountPageCache(accountApi, { tablePage: nextPage });
        // A filter change or a lock can leave the current page past the end.
        if (page > 1 && page > result.page.totalPages) {
          setPage(Math.max(result.page.totalPages, 1));
        }
      })
      .catch((error) => {
        if (error.name !== "AbortError") {
          setTablePage((prev) => ({ ...prev, query: tableQuery, error: accountErrorMessage(error) }));
        }
      });
    return () => controller.abort();
  }, [accountApi, tableQuery, debouncedSearch, roleFilter, departmentFilter, statusFilter, sort, page, pageSize]);

  useEffect(() => {
    if (view.id === undefined) return undefined;
    if (!isCanonicalUuid(view.id)) return undefined;
    const controller = new AbortController();
    accountApi.detail(view.id, controller.signal)
      .then((account) => {
        if (!account) {
          setMissingIds((prev) => [...new Set([...prev, view.id])]);
          return;
        }
        setMissingIds((prev) => prev.filter((id) => id !== view.id));
        setDetails((prev) => ({ ...prev, [view.id]: fromApi(account) }));
      })
      .catch((error) => {
        if (isMissingAccountError(error)) setMissingIds((prev) => [...new Set([...prev, view.id])]);
        else if (error.name !== "AbortError") toast.error(accountErrorMessage(error));
      });
    return () => controller.abort();
  }, [accountApi, view.id]);

  useEffect(() => {
    if (view.name !== "detail" || view.id === undefined || !isCanonicalUuid(view.id)) return undefined;
    const controller = new AbortController();
    Promise.all([
      accountApi.activities(view.id, 100, controller.signal),
      accountApi.workInProgress(view.id, controller.signal),
    ])
      .then(([items, work]) => {
        setActivities(items.map((item) => ({
          ...item,
          time: item.occurredAt,
          title: item.type,
          detail: item.ipAddress || item.outcome,
        })));
        setWorkInProgress(work);
      })
      .catch((error) => error.name !== "AbortError" && toast.error(accountErrorMessage(error)));
    return () => controller.abort();
  }, [accountApi, view.id, view.name, reloadKey]);

  // UC-6: Change History is paginated; another account or a completed action starts again at page 0.
  const requestedHistoryPage = historyPage.accountId === view.id ? historyPage.page : 0;
  useEffect(() => {
    if (view.name !== "detail" || view.id === undefined || !isCanonicalUuid(view.id)) return undefined;
    const controller = new AbortController();
    accountApi.changeHistory(view.id, requestedHistoryPage, 5, controller.signal)
      .then((history) => {
        setChangeHistory(history.content);
        setHistoryPage({
          accountId: view.id,
          page: history.page?.number ?? requestedHistoryPage,
          totalPages: history.page?.totalPages ?? 1,
        });
      })
      .catch((error) => error.name !== "AbortError" && toast.error(accountErrorMessage(error)));
    return () => controller.abort();
  }, [accountApi, view.id, view.name, requestedHistoryPage, reloadKey]);

  const reloadAccounts = () => {
    setHistoryPage((prev) => ({ ...prev, page: 0 }));
    setReloadKey((key) => key + 1);
  };

  useEffect(() => {
    if (view.name !== "profile") return undefined;
    const controller = new AbortController();
    accountApi.profile(controller.signal).then((value) => setProfile(fromApi(value)))
      .catch((error) => error.name !== "AbortError" && toast.error(accountErrorMessage(error)));
    return () => controller.abort();
  }, [accountApi, view.name]);

  const stats = useMemo(() => {
    const total = accounts.length;
    const active = accounts.filter((a) => a.status === "ACTIVE").length;
    const locked = accounts.filter((a) => a.status === "LOCKED").length;
    const pending = accounts.filter((a) => a.passwordChangeRequired).length;

    return { total, active, locked, pending };
  }, [accounts]);

  // Filters change the result set, so return to the first page.
  const withFirstPage = (setter) => (value) => {
    setter(value);
    setPage(1);
  };

  const resetFilters = () => {
    setSearchQuery("");
    setRoleFilter("ALL");
    setDepartmentFilter("ALL");
    setStatusFilter("ALL");
    setSort("createdAt,desc");
    setPage(1);
  };

  // A directly opened URL has no previous page in the app.
  const goBack = () =>
    location.key === "default"
      ? navigate(ADMIN.ACCOUNTS, { replace: true })
      : navigate(-1);

  const showList = () => navigate(ADMIN.ACCOUNTS);
  const openCreate = () => navigate(ADMIN.ACCOUNT_CREATE);

  const openEdit = (account) =>
    navigate(generatePath(ADMIN.ACCOUNT_EDIT, { id: account.id }));

  const openDetail = (id) =>
    navigate(generatePath(ADMIN.ACCOUNT_DETAIL, { id }));
  const openAssignRole = (id) =>
    navigate(generatePath(ADMIN.ACCOUNT_ASSIGN_ROLE, { id }));
  // After creating an account, replace the form with its detail page.
  const replaceWithDetail = (id) =>
    navigate(generatePath(ADMIN.ACCOUNT_DETAIL, { id }), {
      replace: true,
    });

  // UC-7: the account starts ACTIVE and must change its one-time password.
  const createAccount = async (form) => {
    const created = await accountApi.create({
      username: form.username.trim().toLowerCase(),
      role: form.role,
      ...accountRequest(form),
      passwordMode: form.passwordMode || "GENERATE",
      initialPassword: form.passwordMode === "MANUAL" ? form.initialPassword : null,
      submissionId: crypto.randomUUID(),
    });
    const account = fromApi(await accountApi.detail(created.accountId));
    setDetails((prev) => ({ ...prev, [account.id]: account }));
    setPage(1);
    reloadAccounts();

    return {
      account,
      temporaryPassword: created.temporaryPassword,
    };
  };

  // UC-8: update the personal information fields.
  const saveAccount = async (account, form) => {
    const updated = fromApi(await accountApi.update(account.id, {
      ...accountRequest(form),
      // Only a Doctor holds a prescriber link code; other roles always send none.
      maLienthongBacsi: account.role === "DOCTOR" ? form.maLienthongBacsi.trim() || null : null,
      version: account.version,
    }));
    setDetails((prev) => ({ ...prev, [account.id]: updated }));
    reloadAccounts();
    goBack();
  };

  // Confirmation and prompt modal state.
  const [confirmDialog, setConfirmDialog] = useState(null);
  const [lockPrompt, setLockPrompt] = useState(null);
  const [lockReasonInput, setLockReasonInput] = useState("");
  const [lockReasonError, setLockReasonError] = useState("");
  // UC-11 opens as a dialog over the account detail; holds the target account id.
  const [resetPasswordFor, setResetPasswordFor] = useState(null);

  const requestConfirm = (options) => {
    setConfirmDialog(options);
  };

  // UC-9: change an account role and remove any previous room assignment.
  const assignRole = async (account, role, professional = {}) => {
    const hadRoom =
      account.role === "DOCTOR" || account.role === "TECHNICIAN";
    const needsRoom = role === "DOCTOR" || role === "TECHNICIAN";

    let check;
    try {
      check = await accountApi.checkRoleChange(account.id, {
        newRole: role,
        expectedRole: account.role,
        ...professional,
      });
    } catch (error) {
      // UC-09 EF-03: professional-field errors are shown under the fields of the role page.
      const { title, practiceLicenseNo } = accountFieldErrors(error);
      if (title || practiceLicenseNo) {
        return { fieldErrors: { ...(title && { title }), ...(practiceLicenseNo && { practiceLicenseNo }) } };
      }
      toast.error(accountErrorMessage(error, { action: "đổi vai trò", fullName: account.fullName }));
      return;
    }

    if (check.visitsInProgress + check.ordersInProgress > 0) {
      toast.error(`Không thể đổi vai trò: còn ${check.visitsInProgress} lượt khám và ${check.ordersInProgress} chỉ định đang xử lý.`);
      return;
    }

    const lines = [
      `Đổi vai trò của ${account.username}: ${ROLE_LABELS[account.role]} → ${ROLE_LABELS[role]}?`,
      hadRoom && account.roomName
        ? `Phân công phòng ${account.roomName} sẽ bị gỡ.`
        : null,
      "Mọi phiên đăng nhập của tài khoản sẽ bị đăng xuất.",
    ];

    requestConfirm({
      title: "Xác nhận đổi vai trò",
      description: lines.filter(Boolean).join("\n"),
      confirmText: "Đổi vai trò",
      variant: "mint",
      onConfirm: async () => {
        try {
          const updated = fromApi(await accountApi.changeRole(account.id, {
            newRole: role,
            expectedRole: account.role,
            ...professional,
          }));
          setDetails((prev) => ({ ...prev, [account.id]: updated }));
          reloadAccounts();

          toast.success(
            `Đã đổi vai trò của ${account.username} thành ${ROLE_LABELS[role]}`,
          );
          setConfirmDialog(null);

          // Room assignment is a separate UC-15 action from the account detail screen.
          if (needsRoom) {
            toast.info(`Có thể phân công phòng cho ${account.username} từ màn hình chi tiết tài khoản.`, { duration: 5000 });
          }
          openDetail(account.id);
        } catch (error) {
          toast.error(accountErrorMessage(error, { action: "đổi vai trò", fullName: account.fullName }));
        }
      },
    });
  };

  const toggleLock = async (account) => {
    if (account.role === "ADMIN") {
      toast.error(MSG46);
      return;
    }

    const locking = account.status === "ACTIVE";

    if (locking) {
      try {
        const currentWork = await accountApi.workInProgress(account.id);
        if (currentWork.visitsInProgress + currentWork.ordersInProgress > 0) {
          toast.error(msg47(account.fullName));
          return;
        }
      } catch (error) {
        toast.error(accountErrorMessage(error, { action: "khóa", fullName: account.fullName }));
        return;
      }
      setLockReasonInput("");
      setLockReasonError("");
      setLockPrompt(account);
      return;
    }

    requestConfirm({
      title: "Mở khóa tài khoản",
      description: `Bạn có chắc chắn muốn mở khóa tài khoản ${account.username} không?`, // MSG06
      confirmText: "Mở khóa",
      variant: "mint",
      onConfirm: async () => {
        try {
          const updated = fromApi(await accountApi.unlock(account.id, {
            expectedStatus: account.status,
            expectedVersion: await latestVersion(account),
          }));
          setDetails((prev) => ({ ...prev, [account.id]: updated }));
          reloadAccounts();
          toast.success(`Đã mở khóa tài khoản ${account.username}`);
          setConfirmDialog(null);
        } catch (error) {
          toast.error(accountErrorMessage(error, { action: "mở khóa" }));
        }
      },
    });
  };

  // Signing in records lastLoginAt and bumps the account version, so a version loaded before the
  // target signed in would be rejected as CONCURRENT_MODIFICATION (MSG75). Lock/unlock still guard
  // against real conflicts through expectedStatus, which comes from what the Admin is looking at.
  const latestVersion = async (account) => fromApi(await accountApi.detail(account.id)).version;

  const confirmLockAccount = async () => {
    if (!lockPrompt) return;

    const trimmed = lockReasonInput.trim();
    const reasonError = lockReasonMessage(trimmed);

    if (reasonError) {
      setLockReasonError(reasonError);
      return;
    }

    const account = lockPrompt;

    try {
      const updated = fromApi(await accountApi.lock(account.id, {
        reason: trimmed,
        expectedStatus: account.status,
        expectedVersion: await latestVersion(account),
      }));
      setDetails((prev) => ({ ...prev, [account.id]: updated }));
      reloadAccounts();
      toast.success(`Đã khóa tài khoản ${account.username}`);
      setLockPrompt(null);
      setLockReasonInput("");
    } catch (error) {
      toast.error(accountErrorMessage(error, { action: "khóa", fullName: account.fullName }));
    }
  };

  const resetPassword = async (account, request) => {
    // Fetch the latest version right before submitting to avoid CONCURRENT_MODIFICATION
    // when the target account's version changed since the page was loaded (e.g. they logged in).
    const fresh = fromApi(await accountApi.detail(account.id));
    const result = await accountApi.resetPassword(fresh.id, {
      ...request,
      expectedVersion: fresh.version,
    });
    const updated = fromApi(await accountApi.detail(account.id));
    setDetails((prev) => ({ ...prev, [account.id]: updated }));
    reloadAccounts();
    return result;
  };

  const selected = details[view.id];
  const accountNames = new Map(accounts.map((item) => [item.id, item.username]));

  const self = profile || accounts.find(
    (a) => a.username === currentAdmin?.username,
  ) || { status: "ACTIVE", ...currentAdmin };

  const isSelf = (account) =>
    account.username === currentAdmin?.username;

  // Redirect paths not handled by this page to the account list.
  if (!matchedView) {
    return <Navigate to={ADMIN.ACCOUNTS} replace />;
  }

  let subPage = null;

  if (view.name === "create") {
    subPage = (
      <AccountCreateView
        accounts={accounts}
        onCreate={createAccount}
        onCancel={goBack}
        onGoToList={showList}
        onViewDetail={replaceWithDetail}
      />
    );
  } else if (view.name === "profile" && !profile) {
    subPage = <section role="status" className="bg-white rounded-2xl border border-slate-200/70 p-10 text-center text-sm text-slate-500">Đang tải hồ sơ…</section>;
  } else if (view.name === "profile") {
    subPage = (
      <ProfileView
        key={self.id}
        account={self}
        accounts={accounts}
        onSave={async (contact) => {
          const updated = fromApi(await accountApi.updateProfile(contact.phone, contact.email));
          setProfile(updated);
        }}
        onCancel={goBack}
        onChangePassword={onChangePassword}
      />
    );
  } else if (
    selected &&
    selected.status === "LOCKED" &&
    ["edit", "assign-role"].includes(view.name)
  ) {
    subPage = (
      <Navigate
        to={generatePath(ADMIN.ACCOUNT_DETAIL, { id: selected.id })}
        replace
      />
    );
  } else if (
    selected &&
    view.name === "edit" &&
    (isSelf(selected) || selected.role === "ADMIN")
  ) {
    subPage = (
      <Navigate
        to={generatePath(ADMIN.ACCOUNT_DETAIL, { id: selected.id })}
        replace
      />
    );
  } else if (selected && view.name === "edit") {
    subPage = (
      <AccountEditView
        key={`${selected.id}-${selected.updatedAt}`}
        account={selected}
        accounts={accounts}
        isSelf={isSelf(selected)}
        onSave={(form) => saveAccount(selected, form)}
        onCancel={goBack}
        onGoToList={showList}
        onViewDetail={() => openDetail(selected.id)}
        onToggleLock={() => toggleLock(selected)}
      />
    );
  } else if (selected && view.name === "assign-role" && selected.role !== "ADMIN") {
    subPage = (
      <AccountAssignRoleView
        account={selected}
        onCancel={() => openDetail(selected.id)}
        onGoToList={showList}
        onAssign={(role, professional) => assignRole(selected, role, professional)}
      />
    );
  } else if (selected && view.name === "detail") {
    subPage = (
      <>
        <AccountDetailView
          account={selected}
          loginEvents={activities}
          changeHistory={changeHistory}
          historyPage={historyPage.accountId === selected.id ? historyPage : null}
          onHistoryPageChange={(page) => setHistoryPage({ ...historyPage, accountId: selected.id, page })}
          accountNames={accountNames}
          workInProgress={workInProgress}
          isSelf={isSelf(selected)}
          onBack={goBack}
          onGoToList={showList}
          onEdit={() => openEdit(selected)}
          onAssignRole={() => openAssignRole(selected.id)}
          onToggleLock={() => toggleLock(selected)}
          onResetPassword={() => setResetPasswordFor(selected.id)}
        />
        {resetPasswordFor === selected.id && !isSelf(selected) && selected.status !== "LOCKED" && (
          <AccountResetPasswordModal
            account={selected}
            onReset={(request) => resetPassword(selected, request)}
            onClose={() => setResetPasswordFor(null)}
          />
        )}
      </>
    );
  } else if (view.id !== undefined && isCanonicalUuid(view.id) && !missingIds.includes(view.id)) {
    subPage = (
      <section role="status" className="bg-white rounded-2xl border border-slate-200/70 p-10 text-center text-sm text-slate-500">
        Đang tải thông tin tài khoản…
      </section>
    );
  } else if (view.id !== undefined) {
    subPage = (
      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-10 text-center">
        <h1 className="text-xl! font-extrabold! text-[#1b2533]">
          Không tìm thấy tài khoản.
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Tài khoản không tồn tại hoặc đường dẫn không đúng.
        </p>
        <button
          type="button"
          onClick={showList}
          className="mt-5 px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
        >
          Về danh sách tài khoản
        </button>
      </section>
    );
  }

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    onNavigate: (key) => {
      if (key === "accounts") {
        showList();
      } else if (key === "departments" || key === "rooms") {
        navigate(ADMIN.DEPARTMENTS);
      } else if (ADMIN[key.toUpperCase()]) {
        navigate(ADMIN[key.toUpperCase()]);
      }
    },
    activeKey: "accounts",
  };

  const modalElements = (
    <>
      {confirmDialog && (
        <ConfirmModal
          isOpen={Boolean(confirmDialog)}
          title={confirmDialog.title}
          description={confirmDialog.description}
          confirmText={confirmDialog.confirmText}
          variant={confirmDialog.variant}
          onConfirm={confirmDialog.onConfirm}
          onClose={() => setConfirmDialog(null)}
        />
      )}

      {lockPrompt && (
        <ConfirmModal
          isOpen={Boolean(lockPrompt)}
          title={`Khóa tài khoản ${lockPrompt.username}`}
          description="Vui lòng cung cấp lý do khóa tài khoản này (tối đa 500 ký tự)."
          confirmText="Khóa tài khoản"
          variant="destructive"
          onConfirm={confirmLockAccount}
          onClose={() => {
            setLockPrompt(null);
            setLockReasonInput("");
            setLockReasonError("");
          }}
        >
          <div className="mt-3">
            <label
              htmlFor="lock-reason"
              className="block text-xs font-semibold text-slate-700 mb-1.5"
            >
              Lý do khóa <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="lock-reason"
              rows={3}
              value={lockReasonInput}
              onChange={(e) => {
                setLockReasonInput(e.target.value);
                setLockReasonError("");
              }}
              placeholder="Ví dụ: Nghỉ việc, chuyển công tác, vi phạm quy định..."
              maxLength={LOCK_REASON_MAX}
              autoFocus
              aria-invalid={Boolean(lockReasonError)}
              aria-describedby={lockReasonError ? "lock-reason-error" : undefined}
              className="w-full text-xs rounded-xl border border-[#d5e0dc] p-3 focus:outline-none focus:ring-2 focus:ring-[#0d9488] focus:border-transparent"
            />
            <div className="flex justify-between gap-2 mt-1">
              <p id="lock-reason-error" role="alert" className="text-[11px] text-rose-600">
                {lockReasonError}
              </p>
              <span className="text-[11px] text-slate-400 shrink-0">
                {lockReasonInput.length}/{LOCK_REASON_MAX} ký tự
              </span>
            </div>
          </div>
        </ConfirmModal>
      )}

    </>
  );

  if (subPage) {
    return (
      <AdminLayout {...layoutProps}>
        <div className="px-6 pb-8">{subPage}</div>
        {modalElements}
      </AdminLayout>
    );
  }

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        {loading && tablePage.content.length > 0 && (
          <div role="status" className="p-3 rounded-xl bg-teal-50 border border-teal-200 text-xs font-semibold text-teal-700">
            Đang cập nhật danh sách tài khoản…
          </div>
        )}
        {loadError && <div role="alert" className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-sm text-rose-700">Không thể tải tài khoản: {loadError}</div>}
        {notice && (
          <div
            role="status"
            className="p-3.5 rounded-xl bg-teal-50 border border-teal-200 text-teal-800 text-xs font-semibold flex items-center gap-2"
          >
            <CheckCircle2 className="w-4 h-4 text-[#127e7a]" />
            <span>{notice}</span>
          </div>
        )}

        <div className="flex flex-col items-start gap-3">
          <nav
            aria-label="Breadcrumb"
            className="flex items-center gap-1.5 text-xs text-slate-500"
          >
            <House className="w-3.5 h-3.5" />
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-slate-700 font-medium">
              Quản lý tài khoản
            </span>
          </nav>

          <div>
            <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">
              Quản lý tài khoản
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Quản lý tài khoản người dùng, vai trò và quyền truy cập hệ thống
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
          <AccountStatCard
            title="Tổng tài khoản"
            value={stats.total}
            icon={Users}
            iconTone="bg-[#e7f0fd] text-[#3b7be0]"
            note="Tất cả tài khoản nhân viên"
          />
          <AccountStatCard
            title="Đang hoạt động"
            value={stats.active}
            icon={UserCheck}
            iconTone="bg-[#e2f5ee] text-[#1f9d70]"
            highlight={percentOf(stats.active, stats.total)}
            highlightTone="text-[#1f9d70]"
            note="trên tổng số tài khoản"
          />
          <AccountStatCard
            title="Đã khóa"
            value={stats.locked}
            icon={Lock}
            iconTone="bg-[#fde9eb] text-[#e5484d]"
            highlight={percentOf(stats.locked, stats.total)}
            highlightTone="text-[#e5484d]"
            note="trên tổng số tài khoản"
          />
          <AccountStatCard
            title="Chờ đổi mật khẩu"
            value={stats.pending}
            icon={KeyRound}
            iconTone="bg-[#fdf1dc] text-[#e9a23b]"
            highlight={percentOf(stats.pending, stats.total)}
            highlightTone="text-slate-600"
            note="cần đổi khi đăng nhập"
          />
        </div>

        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">
              Danh sách tài khoản
            </h2>
            <button
              type="button"
              onClick={openCreate}
              className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm transition-all cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Tạo tài khoản</span>
            </button>
          </div>

          <AccountFilterBar
            searchQuery={searchQuery}
            setSearchQuery={withFirstPage(setSearchQuery)}
            roleFilter={roleFilter}
            setRoleFilter={withFirstPage(setRoleFilter)}
            statusFilter={statusFilter}
            setStatusFilter={withFirstPage(setStatusFilter)}
            departmentFilter={departmentFilter}
            setDepartmentFilter={withFirstPage(setDepartmentFilter)}
            departments={departments}
            onReset={resetFilters}
          />

          <AccountTable
            accounts={tablePage.content}
            loading={loading}
            totalCount={tablePage.totalElements}
            page={page}
            pageSize={pageSize}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
              setPageSize(size);
              setPage(1);
            }}
            onView={(account) => openDetail(account.id)}
            sort={sort}
            onSortChange={withFirstPage(setSort)}
          />
        </section>
      </div>

      {modalElements}
    </AdminLayout>
  );
}

export default AccountManagementPage;
