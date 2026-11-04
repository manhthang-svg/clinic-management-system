import { useCallback, useEffect, useState, useMemo } from "react";
import { Navigate, generatePath, matchPath, useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import {
  FileText,
  CircleCheck,
  CircleMinus,
  ShieldPlus,
  Plus,
} from "lucide-react";
import { AdminLayout } from "../../layouts";
import { AccountStatCard } from "../../components/accounts";
import { AccountPageHeader } from "../../components/accounts/AccountFormParts";
import { percentOf } from "../../components/accounts/accountFormat";
import {
  ServiceFilterBar,
  ServiceTable,
  ServiceCreateView,
  ServiceDetailView,
} from "../../components/services";
import { ConfirmModal } from "../../components/ui";
import { serviceErrorMessage, serviceStatusErrorMessage } from "../../components/services/serviceErrors";
import {
  formToServiceRequest,
  infoFormToRequest,
  priceFormToRequest,
} from "../../components/services/serviceForm";
import { createServiceApi } from "../../apis/serviceApi";
import { apiClient } from "../../services/session";
import { PAGE_SIZE_OPTIONS } from "../../constants";
import { ROUTES } from "../../routes/routes";

const { ADMIN } = ROUTES;

const defaultServiceApi = createServiceApi(apiClient);

const SEARCH_DEBOUNCE_MS = 300;
const SUMMARY_PAGE_SIZE = 50;

async function listAllServices(serviceApi, signal) {
  const services = [];
  for (let page = 0; ; page += 1) {
    const result = await serviceApi.list({ page, size: SUMMARY_PAGE_SIZE }, signal);
    services.push(...result.content);
    if (page + 1 >= result.page.totalPages) return services;
  }
}

export function ServiceManagementPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  onNavigate,
  serviceApi = defaultServiceApi,
}) {
  const [allServices, setAllServices] = useState([]);
  const [reloadKey, setReloadKey] = useState(0);
  const [groups, setGroups] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [labParameters, setLabParameters] = useState([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [groupFilter, setGroupFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
  const [tablePage, setTablePage] = useState({
    query: null,
    content: [],
    totalElements: 0,
    error: null,
  });

  // { id, service, error } of the detail screen; service is null while loading.
  const [detail, setDetail] = useState({ id: null, service: null, error: null });
  const [detailReloadKey, setDetailReloadKey] = useState(0);
  // UC-25: { target, unfinishedOrderCount } once the server check passes.
  const [statusConfirm, setStatusConfirm] = useState(null);
  const [statusBusy, setStatusBusy] = useState(false);

  // The URL decides whether the list, the create form, or a detail is shown.
  // "/new" also matches ":id", so the create route is checked first.
  const location = useLocation();
  const navigate = useNavigate();
  const creating = Boolean(matchPath(ADMIN.SERVICE_CREATE, location.pathname));
  const detailId = creating ? null : matchPath(ADMIN.SERVICE_DETAIL, location.pathname)?.params.id ?? null;
  const known = creating || detailId !== null || Boolean(matchPath(ADMIN.SERVICES, location.pathname));

  const showList = () => navigate(ADMIN.SERVICES);

  // A directly opened URL has no previous page in the app.
  const goBack = () =>
    location.key === "default"
      ? navigate(ADMIN.SERVICES, { replace: true })
      : navigate(-1);

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedSearch(searchQuery.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [searchQuery]);

  const loadFormOptions = useCallback((signal) =>
    serviceApi.formOptions(signal).then((options) => {
      setGroups(options.serviceGroups ?? []);
      setDepartments(options.departments ?? []);
      setLabParameters(options.labParameters ?? []);
    }), [serviceApi]);

  useEffect(() => {
    const controller = new AbortController();
    loadFormOptions(controller.signal)
      .catch((error) => error.name !== "AbortError" && toast.error(serviceErrorMessage(error)));
    return () => controller.abort();
  }, [loadFormOptions]);

  useEffect(() => {
    const controller = new AbortController();
    listAllServices(serviceApi, controller.signal)
      .then(setAllServices)
      .catch((error) => error.name !== "AbortError" && toast.error(serviceErrorMessage(error)));
    return () => controller.abort();
  }, [serviceApi, reloadKey]);

  // UC-21: load the service the detail URL names.
  useEffect(() => {
    if (detailId === null) return undefined;
    const controller = new AbortController();
    serviceApi.detail(detailId, controller.signal)
      .then((service) => setDetail({ id: detailId, service, error: null }))
      .catch((error) => {
        if (error.name !== "AbortError") setDetail({ id: detailId, service: null, error });
      });
    return () => controller.abort();
  }, [serviceApi, detailId, detailReloadKey]);

  // UC-20: the server searches, filters, and pages the table.
  const tableQuery = JSON.stringify([debouncedSearch, typeFilter, groupFilter, statusFilter, page, pageSize, reloadKey]);
  const loading = tablePage.query !== tableQuery;
  const loadError = loading ? null : tablePage.error;
  const filtered =
    debouncedSearch !== "" || typeFilter !== "ALL" || groupFilter !== "ALL" || statusFilter !== "ALL";

  useEffect(() => {
    const controller = new AbortController();
    serviceApi.list({
      keyword: debouncedSearch,
      type: typeFilter === "ALL" ? undefined : typeFilter,
      group: groupFilter === "ALL" ? undefined : groupFilter,
      status: statusFilter === "ALL" ? undefined : statusFilter,
      page: page - 1,
      size: pageSize,
    }, controller.signal)
      .then((result) => {
        setTablePage({
          query: tableQuery,
          content: result.content,
          totalElements: result.page.totalElements,
          error: null,
        });
        // A filter change can leave the current page past the end.
        if (page > 1 && page > result.page.totalPages) {
          setPage(Math.max(result.page.totalPages, 1));
        }
      })
      .catch((error) => {
        if (error.name !== "AbortError") {
          setTablePage((prev) => ({ ...prev, query: tableQuery, error: serviceErrorMessage(error) }));
        }
      });
    return () => controller.abort();
  }, [serviceApi, tableQuery, debouncedSearch, typeFilter, groupFilter, statusFilter, page, pageSize]);

  const stats = useMemo(() => {
    const total = allServices.length;
    const active = allServices.filter((s) => s.status === "ACTIVE").length;
    const insured = allServices.filter((s) => s.insurancePrice !== null && s.insurancePrice !== undefined).length;

    return { total, active, inactive: total - active, insured };
  }, [allServices]);

  // Filters change the result set, so return to the first page.
  const withFirstPage = (setter) => (value) => {
    setter(value);
    setPage(1);
  };

  const resetFilters = () => {
    setSearchQuery("");
    setTypeFilter("ALL");
    setGroupFilter("ALL");
    setStatusFilter("ALL");
    setPage(1);
  };

  const createService = async (form) => {
    const request = formToServiceRequest(form);
    await serviceApi.create(request);
    toast.success(`Đã thêm dịch vụ ${request.code.toUpperCase()}`);
    setReloadKey((key) => key + 1);
    navigate(ADMIN.SERVICES, { replace: true });
  };

  // UC-23 / UC-24 send the loaded version; a stale one (409) reloads the detail and keeps the dialog open.
  const updateDetail = async (send, message) => {
    const { id, service } = detail;
    try {
      const updated = await send(id, service.version);
      setDetail({ id, service: updated, error: null });
      setReloadKey((key) => key + 1);
      toast.success(message);
    } catch (error) {
      if (error.errorResponse?.code === "CONCURRENT_MODIFICATION") {
        setDetailReloadKey((key) => key + 1);
      }
      throw error;
    }
  };

  const updateInfo = (form) =>
    updateDetail(
      (id, version) => serviceApi.update(id, infoFormToRequest(form, version)),
      "Đã cập nhật thông tin dịch vụ",
    );

  const updatePrices = (form) =>
    updateDetail(
      (id, version) => serviceApi.updatePrices(id, priceFormToRequest(form, version)),
      "Đã cập nhật giá dịch vụ",
    );

  // UC-25: the server checks the change first, then the admin confirms it.
  const requestStatusChange = async () => {
    const { id, service } = detail;
    const deactivating = service.status === "ACTIVE";
    setStatusBusy(true);
    try {
      const check = deactivating
        ? await serviceApi.checkDeactivation(id)
        : await serviceApi.checkActivation(id);
      setStatusConfirm({ target: check.targetStatus, unfinishedOrderCount: check.unfinishedOrderCount });
    } catch (error) {
      toast.error(serviceStatusErrorMessage(error));
    } finally {
      setStatusBusy(false);
    }
  };

  const confirmStatusChange = async () => {
    if (statusBusy) return;
    const { target } = statusConfirm;
    setStatusBusy(true);
    try {
      await updateDetail(
        (id, version) => serviceApi.updateStatus(id, { status: target, version }),
        target === "INACTIVE" ? "Đã ngừng hoạt động dịch vụ" : "Đã kích hoạt dịch vụ",
      );
    } catch (error) {
      toast.error(serviceStatusErrorMessage(error));
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
      if (key === "services") showList();
      else onNavigate?.(key);
    },
    activeKey: "services",
  };

  if (!known) {
    return <Navigate to={ADMIN.SERVICES} replace />;
  }

  if (creating) {
    return (
      <AdminLayout {...layoutProps}>
        <div className="px-6 pb-8">
          <ServiceCreateView
            groups={groups}
            departments={departments}
            labParameters={labParameters}
            onCreate={createService}
            onCancel={goBack}
            onGoToList={showList}
          />
        </div>
      </AdminLayout>
    );
  }

  if (detailId !== null) {
    const current = detail.id === detailId ? detail : { service: null, error: null };
    const notFound = current.error?.status === 404 || current.error?.status === 400;

    let content;
    if (current.service) {
      const deactivating = statusConfirm?.target === "INACTIVE";
      content = (
        <>
          <ServiceDetailView
            service={current.service}
            onGoToList={showList}
            onUpdateInfo={updateInfo}
            onUpdatePrices={updatePrices}
            onToggleStatus={requestStatusChange}
            statusBusy={statusBusy}
          />
          <ConfirmModal
            isOpen={Boolean(statusConfirm)}
            title={deactivating ? "Ngừng hoạt động dịch vụ" : "Kích hoạt dịch vụ"}
            description={
              deactivating
                ? `Dịch vụ "${current.service.name}" sẽ bị ẩn khỏi danh sách chỉ định.` +
                  (statusConfirm?.unfinishedOrderCount > 0
                    ? `\nĐang có ${statusConfirm.unfinishedOrderCount} chỉ định chưa hoàn thành dùng dịch vụ này.`
                    : "")
                : `Kích hoạt lại dịch vụ "${current.service.name}"?`
            }
            confirmText={statusBusy ? "Đang cập nhật…" : deactivating ? "Ngừng hoạt động" : "Kích hoạt"}
            variant={deactivating ? "destructive" : "mint"}
            onConfirm={confirmStatusChange}
            onClose={() => !statusBusy && setStatusConfirm(null)}
          />
        </>
      );
    } else if (current.error) {
      content = (
        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-10 text-center">
          <h1 className="text-xl! font-extrabold! text-[#1b2533]">
            {notFound ? "Không tìm thấy dịch vụ" : "Không tải được dịch vụ"}
          </h1>
          <p role="alert" className="text-sm text-slate-500 mt-1">
            {notFound
              ? "Dịch vụ không tồn tại hoặc đường dẫn không đúng."
              : "Đã xảy ra lỗi khi tải dịch vụ. Vui lòng thử lại."}
          </p>
          <button
            type="button"
            onClick={showList}
            className="mt-5 px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
          >
            Về danh sách dịch vụ
          </button>
        </section>
      );
    } else {
      content = (
        <p role="status" className="p-10 text-center text-sm text-slate-400">
          Đang tải thông tin dịch vụ…
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
          title="Quản lý dịch vụ và giá"
          description="Danh mục dịch vụ khám và cận lâm sàng, giá viện phí và giá BHYT"
          crumbs={[{ label: "Quản lý dịch vụ và giá" }]}
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
          <AccountStatCard
            title="Tổng số dịch vụ"
            value={stats.total}
            icon={FileText}
            iconTone="bg-[#e7f0fd] text-[#3b7be0]"
            note="trong danh mục"
          />
          <AccountStatCard
            title="Đang hoạt động"
            value={stats.active}
            icon={CircleCheck}
            iconTone="bg-[#e2f5ee] text-[#1f9d70]"
            highlight={percentOf(stats.active, stats.total)}
            highlightTone="text-[#1f9d70]"
            note="hiển thị khi chỉ định"
          />
          <AccountStatCard
            title="Ngừng hoạt động"
            value={stats.inactive}
            icon={CircleMinus}
            iconTone="bg-slate-100 text-slate-500"
            highlight={percentOf(stats.inactive, stats.total)}
            highlightTone="text-slate-600"
            note="ẩn khỏi danh sách chỉ định"
          />
          <AccountStatCard
            title="Thuộc phạm vi BHYT"
            value={stats.insured}
            icon={ShieldPlus}
            iconTone="bg-[#fdf1dc] text-[#e9a23b]"
            highlight={percentOf(stats.insured, stats.total)}
            highlightTone="text-[#c98a1b]"
            note="có giá BHYT"
          />
        </div>

        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">
              Danh sách dịch vụ
            </h2>
            <button
              type="button"
              onClick={() => navigate(ADMIN.SERVICE_CREATE)}
              className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm transition-all cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Thêm dịch vụ</span>
            </button>
          </div>

          <ServiceFilterBar
            searchQuery={searchQuery}
            setSearchQuery={withFirstPage(setSearchQuery)}
            typeFilter={typeFilter}
            setTypeFilter={withFirstPage(setTypeFilter)}
            groupFilter={groupFilter}
            setGroupFilter={withFirstPage(setGroupFilter)}
            statusFilter={statusFilter}
            setStatusFilter={withFirstPage(setStatusFilter)}
            onReset={resetFilters}
            groups={groups}
          />

          <ServiceTable
            services={tablePage.content}
            loading={loading}
            error={loadError}
            filtered={filtered}
            totalCount={tablePage.totalElements}
            page={page}
            pageSize={pageSize}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
              setPageSize(size);
              setPage(1);
            }}
            onView={(service) => navigate(generatePath(ADMIN.SERVICE_DETAIL, { id: service.id }))}
          />
        </section>
      </div>
    </AdminLayout>
  );
}

export default ServiceManagementPage;
