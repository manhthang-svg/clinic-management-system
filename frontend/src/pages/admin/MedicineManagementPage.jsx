import { useCallback, useEffect, useState } from "react";
import {
  Navigate,
  generatePath,
  matchPath,
  useLocation,
  useNavigate,
} from "react-router";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { AdminLayout } from "../../layouts";
import { AccountPageHeader } from "../../components/accounts/AccountFormParts";
import {
  MedicineDetailView,
  MedicineFilterBar,
  MedicineFormModal,
  MedicineTable,
} from "../../components/medicines";
import {
  formToCreateRequest,
  formToUpdateRequest,
} from "../../components/medicines/medicineForm";
import {
  medicineLoadErrorMessage,
  medicineStatusErrorMessage,
} from "../../components/medicines/medicineErrors";
import {
  MSG80,
  msg03,
  msg04,
  msg06,
  msg39,
} from "../../components/services/serviceMessages";
import { ConfirmModal } from "../../components/ui";
import { createMedicineApi } from "../../apis/medicineApi";
import { apiClient } from "../../services/session";
import { PAGE_SIZE_OPTIONS } from "../../constants";
import { ROUTES } from "../../routes/routes";

const { ADMIN } = ROUTES;
const defaultMedicineApi = createMedicineApi(apiClient);
const SEARCH_DEBOUNCE_MS = 300;
// ASSUMPTION: the SRS sets no response time for UC-81 to UC-84; 3 s bounds a request that never answers
// (e.g. the network is turned off), after which MSG80 is shown.
const REQUEST_TIMEOUT_MS = 3000;

/** Runs request(signal) and aborts it after REQUEST_TIMEOUT_MS, so the caller gets an AbortError (shown as MSG80). */
async function withTimeout(request) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await request(controller.signal);
  } finally {
    clearTimeout(timer);
  }
}
const NO_FILTERS = {
  keyword: "",
  route: "ALL",
  unit: "ALL",
  controlGroup: "ALL",
  status: "ALL",
};

const orUndefined = (value) => (value === "ALL" ? undefined : value);

/** UC-81 to UC-84: the Admin views, creates, updates, activates and deactivates medicines. */
export function MedicineManagementPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  onNavigate,
  medicineApi = defaultMedicineApi,
}) {
  const [filters, setFilters] = useState(NO_FILTERS);
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
  const [reloadKey, setReloadKey] = useState(0);
  const [tablePage, setTablePage] = useState({
    query: null,
    content: [],
    totalElements: 0,
    error: null,
  });
  const [options, setOptions] = useState({ routes: [], units: [] });
  const [creating, setCreating] = useState(false);

  // { id, medicine, error } of the detail screen; medicine is null while loading.
  const [detail, setDetail] = useState({
    id: null,
    medicine: null,
    error: null,
  });
  const [detailReloadKey, setDetailReloadKey] = useState(0);
  const [editing, setEditing] = useState(false);
  const [statusConfirm, setStatusConfirm] = useState(null);
  const [statusBusy, setStatusBusy] = useState(false);
  const [statusError, setStatusError] = useState(null);

  const location = useLocation();
  const navigate = useNavigate();
  const detailId =
    matchPath(ADMIN.MEDICINE_DETAIL, location.pathname)?.params.id ?? null;
  const known =
    detailId !== null || Boolean(matchPath(ADMIN.MEDICINES, location.pathname));
  const showList = () => navigate(ADMIN.MEDICINES);

  useEffect(() => {
    const handle = setTimeout(
      () => setDebouncedKeyword(filters.keyword.trim()),
      SEARCH_DEBOUNCE_MS,
    );
    return () => clearTimeout(handle);
  }, [filters.keyword]);

  const loadOptions = useCallback(
    (signal) =>
      medicineApi
        .formOptions(signal)
        .then((result) =>
          setOptions({
            routes: result.routes ?? [],
            units: result.units ?? [],
          }),
        )
        .catch(
          (error) =>
            error.name !== "AbortError" &&
            toast.error(medicineLoadErrorMessage(error)),
        ),
    [medicineApi],
  );

  useEffect(() => {
    const controller = new AbortController();
    loadOptions(controller.signal);
    return () => controller.abort();
  }, [loadOptions, reloadKey]);

  // UC-81: the server searches, filters and pages the list.
  const tableQuery = JSON.stringify([
    debouncedKeyword,
    filters.route,
    filters.unit,
    filters.controlGroup,
    filters.status,
    page,
    pageSize,
    reloadKey,
  ]);
  const loading = tablePage.query !== tableQuery;
  const filtered =
    debouncedKeyword !== "" ||
    filters.route !== "ALL" ||
    filters.unit !== "ALL" ||
    filters.controlGroup !== "ALL" ||
    filters.status !== "ALL";

  // After a failed load, reload the list and form options once the browser is back online.
  const listFailed = Boolean(tablePage.error);
  useEffect(() => {
    if (!listFailed) return undefined;
    const reload = () => setReloadKey((key) => key + 1);
    window.addEventListener("online", reload);
    return () => window.removeEventListener("online", reload);
  }, [listFailed]);

  useEffect(() => {
    const controller = new AbortController();
    // A list request that never answers would leave stale rows under the new filters; give up and show MSG80.
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, REQUEST_TIMEOUT_MS);
    medicineApi
      .list(
        {
          keyword: debouncedKeyword,
          route: orUndefined(filters.route),
          unit: orUndefined(filters.unit),
          controlGroup: orUndefined(filters.controlGroup),
          status: orUndefined(filters.status),
          page: page - 1,
          size: pageSize,
        },
        controller.signal,
      )
      .then((result) => {
        setTablePage({
          query: tableQuery,
          content: result.content,
          totalElements: result.page.totalElements,
          error: null,
        });
        if (page > 1 && page > result.page.totalPages)
          setPage(Math.max(result.page.totalPages, 1));
      })
      .catch((error) => {
        if (timedOut || error.name !== "AbortError") {
          const message = timedOut ? MSG80 : medicineLoadErrorMessage(error);
          setTablePage((previous) => ({
            ...previous,
            query: tableQuery,
            error: message,
          }));
        }
      })
      .finally(() => clearTimeout(timer));
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [
    medicineApi,
    tableQuery,
    debouncedKeyword,
    filters.route,
    filters.unit,
    filters.controlGroup,
    filters.status,
    page,
    pageSize,
  ]);

  // UC-81 detail.
  useEffect(() => {
    if (detailId === null) return undefined;
    const controller = new AbortController();
    // A detail request that never answers would leave "Đang tải…" forever; give up and show MSG80.
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, REQUEST_TIMEOUT_MS);
    medicineApi
      .detail(detailId, controller.signal)
      .then((medicine) => setDetail({ id: detailId, medicine, error: null }))
      .catch((error) => {
        if (timedOut || error.name !== "AbortError")
          setDetail({ id: detailId, medicine: null, error });
      })
      .finally(() => clearTimeout(timer));
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [medicineApi, detailId, detailReloadKey]);

  const changeFilters = (next) => {
    setFilters(next);
    setPage(1);
  };

  // UC-82: errors are thrown back to the dialog, which shows them.
  const createMedicine = async (form) => {
    const request = formToCreateRequest(form);
    await withTimeout((signal) => medicineApi.create(request, signal));
    toast.success(msg04(`thuốc ${request.code}`)); // MSG04
    setCreating(false);
    setReloadKey((key) => key + 1);
  };

  // UC-83: a stale version (409) reloads the detail and keeps the dialog open with MSG75.
  const updateMedicine = async (form) => {
    const { id, medicine } = detail;
    try {
      const updated = await withTimeout((signal) =>
        medicineApi.update(id, formToUpdateRequest(form, medicine.version), signal),
      );
      setDetail({ id, medicine: updated, error: null });
      setEditing(false);
      setReloadKey((key) => key + 1);
      toast.success(msg03(`Thông tin thuốc ${medicine.code}`)); // MSG03
    } catch (error) {
      if (error.errorResponse?.code === "CONCURRENT_MODIFICATION")
        setDetailReloadKey((key) => key + 1);
      throw error;
    }
  };

  // UC-84: MSG06 confirms the change first.
  const confirmStatusChange = async () => {
    if (statusBusy) return;
    const { id, medicine } = detail;
    setStatusBusy(true);
    setStatusError(null);
    try {
      const updated = await withTimeout((signal) =>
        medicineApi.updateStatus(
          id,
          { status: statusConfirm.target, version: medicine.version },
          signal,
        ),
      );
      setDetail({ id, medicine: updated, error: null });
      setReloadKey((key) => key + 1);
      toast.success(msg03(`Trạng thái thuốc ${medicine.code}`)); // MSG03
    } catch (error) {
      setStatusError(medicineStatusErrorMessage(error));
      if (error.errorResponse?.code === "CONCURRENT_MODIFICATION")
        setDetailReloadKey((key) => key + 1);
    } finally {
      setStatusBusy(false);
      setStatusConfirm(null);
    }
  };

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    onNavigate: (key) => {
      if (key === "medicines") showList();
      else onNavigate?.(key);
    },
    activeKey: "medicines",
  };

  if (!known) return <Navigate to={ADMIN.MEDICINES} replace />;

  if (detailId !== null) {
    const current =
      detail.id === detailId ? detail : { medicine: null, error: null };
    const notFound =
      current.error?.status === 404 || current.error?.status === 400;
    let content;
    if (current.medicine) {
      const deactivating = statusConfirm?.target === "INACTIVE";
      content = (
        <>
          <MedicineDetailView
            medicine={current.medicine}
            onGoToList={showList}
            onEdit={() => setEditing(true)}
            onToggleStatus={() =>
              setStatusConfirm({
                target:
                  current.medicine.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
              })
            }
            statusBusy={statusBusy}
            statusError={statusError}
          />
          {editing && (
            <MedicineFormModal
              medicine={current.medicine}
              routes={options.routes}
              units={options.units}
              onSubmit={updateMedicine}
              onClose={() => setEditing(false)}
            />
          )}
          <ConfirmModal
            isOpen={Boolean(statusConfirm)}
            title={deactivating ? "Ngừng sử dụng thuốc" : "Kích hoạt thuốc"}
            description={msg06(
              `${deactivating ? "ngừng sử dụng" : "kích hoạt"} thuốc "${current.medicine.name}"`,
            )}
            confirmText={
              statusBusy
                ? "Đang cập nhật…"
                : deactivating
                  ? "Ngừng sử dụng"
                  : "Kích hoạt"
            }
            variant={deactivating ? "destructive" : "mint"}
            onConfirm={confirmStatusChange}
            onClose={() => !statusBusy && setStatusConfirm(null)}
          />
        </>
      );
    } else if (current.error) {
      content = (
        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-10 text-center">
          <h1 role="alert" className="text-xl! font-extrabold! text-[#1b2533]">
            {notFound ? msg39("thuốc") : MSG80}
          </h1>
          <button
            type="button"
            onClick={showList}
            className="mt-5 px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
          >
            Về danh mục thuốc
          </button>
        </section>
      );
    } else {
      content = (
        <p role="status" className="p-10 text-center text-sm text-slate-400">
          Đang tải thông tin thuốc…
        </p>
      );
    }
    return (
      <AdminLayout {...layoutProps}>
        <div className="px-6 pb-8">{content}</div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        <AccountPageHeader
          title="Danh mục thuốc"
          description="Thuốc dùng để kê đơn; cơ sở không bán và không cấp phát thuốc"
          crumbs={[{ label: "Danh mục thuốc" }]}
        />
        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">
              Danh sách thuốc
            </h2>
            <button
              type="button"
              onClick={() => setCreating(true)}
              className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm transition-all cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Thêm thuốc</span>
            </button>
          </div>
          <MedicineFilterBar
            filters={filters}
            onChange={changeFilters}
            onReset={() => changeFilters(NO_FILTERS)}
            routes={options.routes}
            units={options.units}
          />
          <MedicineTable
            medicines={tablePage.content}
            loading={loading}
            error={loading ? null : tablePage.error}
            filtered={filtered}
            totalCount={tablePage.totalElements}
            page={page}
            pageSize={pageSize}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
              setPageSize(size);
              setPage(1);
            }}
            onView={(medicine) =>
              navigate(generatePath(ADMIN.MEDICINE_DETAIL, { id: medicine.id }))
            }
          />
        </section>
      </div>
      {creating && (
        <MedicineFormModal
          routes={options.routes}
          units={options.units}
          onSubmit={createMedicine}
          onClose={() => setCreating(false)}
        />
      )}
    </AdminLayout>
  );
}

export default MedicineManagementPage;
