import { useEffect, useState, useMemo } from "react";
import { Navigate, matchPath, useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import {
  FileText,
  CircleCheck,
  CircleMinus,
  ShieldPlus,
  Plus,
  ChevronRight,
} from "lucide-react";
import { AdminLayout } from "../../layouts";
import { AccountStatCard } from "../../components/accounts";
import { percentOf } from "../../components/accounts/accountFormat";
import {
  ServiceFilterBar,
  ServiceTable,
  ServiceOverviewCard,
  ServiceCreateView,
} from "../../components/services";
import { serviceErrorMessage } from "../../components/services/serviceErrors";
import { formToServiceRequest } from "../../components/services/serviceForm";
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

  // The URL decides whether the list or the create form is shown.
  const location = useLocation();
  const navigate = useNavigate();
  const creating = Boolean(matchPath(ADMIN.SERVICE_CREATE, location.pathname));
  const known = creating || Boolean(matchPath(ADMIN.SERVICES, location.pathname));

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

  useEffect(() => {
    const controller = new AbortController();
    serviceApi.formOptions(controller.signal)
      .then((options) => {
        setGroups(options.serviceGroups ?? []);
        setDepartments(options.departments ?? []);
      })
      .catch((error) => error.name !== "AbortError" && toast.error(serviceErrorMessage(error)));
    return () => controller.abort();
  }, [serviceApi]);

  useEffect(() => {
    const controller = new AbortController();
    listAllServices(serviceApi, controller.signal)
      .then(setAllServices)
      .catch((error) => error.name !== "AbortError" && toast.error(serviceErrorMessage(error)));
    return () => controller.abort();
  }, [serviceApi, reloadKey]);

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
            onCreate={createService}
            onCancel={goBack}
            onGoToList={showList}
          />
        </div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">
              Quản lý dịch vụ và giá
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Danh mục dịch vụ khám và cận lâm sàng, giá viện phí và giá BHYT
            </p>
          </div>

          <nav
            aria-label="Breadcrumb"
            className="flex items-center gap-1.5 text-xs text-slate-500"
          >
            <span>Quản trị</span>
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-slate-700 font-medium">Dịch vụ và giá</span>
          </nav>
        </div>

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

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_248px] gap-5 items-start">
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
            />
          </section>

          <aside className="space-y-5">
            <ServiceOverviewCard services={allServices} />
          </aside>
        </div>
      </div>
    </AdminLayout>
  );
}

export default ServiceManagementPage;
