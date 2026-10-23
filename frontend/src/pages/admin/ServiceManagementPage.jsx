import { useState, useMemo } from "react";
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
import {
  normalizeSearch,
  percentOf,
} from "../../components/accounts/accountFormat";
import {
  ServiceFilterBar,
  ServiceTable,
  ServiceOverviewCard,
  ServiceActivityCard,
} from "../../components/services";

export function ServiceManagementPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  onNavigate,
}) {
  const [services] = useState([]);
  const [activities] = useState([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [groupFilter, setGroupFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const filteredServices = useMemo(() => {
    const term = normalizeSearch(searchQuery);

    return services.filter((service) => {
      const matchSearch =
        term === "" ||
        [service.code, service.name].some((field) =>
          normalizeSearch(field).includes(term),
        );

      const matchType = typeFilter === "ALL" || service.type === typeFilter;
      const matchGroup = groupFilter === "ALL" || service.group === groupFilter;
      const matchStatus =
        statusFilter === "ALL" || service.status === statusFilter;

      return matchSearch && matchType && matchGroup && matchStatus;
    });
  }, [services, searchQuery, typeFilter, groupFilter, statusFilter]);

  const stats = useMemo(() => {
    const total = services.length;
    const active = services.filter((s) => s.status === "ACTIVE").length;
    const insured = services.filter((s) => s.insurancePrice !== null).length;

    return { total, active, inactive: total - active, insured };
  }, [services]);

  const resetFilters = () => {
    setSearchQuery("");
    setTypeFilter("ALL");
    setGroupFilter("ALL");
    setStatusFilter("ALL");
  };

  return (
    <AdminLayout
      currentAdmin={currentAdmin}
      onLogout={onLogout}
      onChangePassword={onChangePassword}
      onNavigate={onNavigate}
      activeKey="services"
    >
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
              {/* TBD: no use case creates services; the catalog is seeded (docs/state-transitions.md). */}
              <button
                type="button"
                className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm transition-all cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                <span>Thêm dịch vụ</span>
              </button>
            </div>

            <ServiceFilterBar
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              typeFilter={typeFilter}
              setTypeFilter={setTypeFilter}
              groupFilter={groupFilter}
              setGroupFilter={setGroupFilter}
              statusFilter={statusFilter}
              setStatusFilter={setStatusFilter}
              onReset={resetFilters}
            />

            <ServiceTable
              services={filteredServices}
              totalCount={services.length}
            />
          </section>

          <aside className="space-y-5">
            <ServiceOverviewCard services={services} />
            <ServiceActivityCard activities={activities} />
          </aside>
        </div>
      </div>
    </AdminLayout>
  );
}

export default ServiceManagementPage;
