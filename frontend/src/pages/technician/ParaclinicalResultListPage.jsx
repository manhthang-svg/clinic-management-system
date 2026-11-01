import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { FileEdit, Info } from 'lucide-react';
import { FormAlert } from '../../components/shared/FormFields';
import { resultMockApi } from '../../components/paraclinicalResult';
import { KIND_LABEL, formatDateTime } from '../../components/paraclinicalResult/resultForm';

const TABS = [
  { id: 'pending', label: 'Chờ nhập kết quả' },
  { id: 'submitted', label: 'Đã gửi' },
];

function statusOf(order) {
  if (order.result?.status === 'SUBMITTED') return { label: 'Đã gửi', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' };
  if (order.result) return { label: 'Bản nháp', className: 'bg-amber-50 text-amber-700 border-amber-200' };
  return { label: 'Chưa nhập', className: 'bg-slate-100 text-slate-600 border-slate-200' };
}

/** Entry to the UC-67 result slips, close to UC-64 (pending results). Demo data until the API exists. */
export function ParaclinicalResultListPage({ api = resultMockApi }) {
  const navigate = useNavigate();
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('pending');
  const [onlyMine, setOnlyMine] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listPending();
        if (!cancelled) setOrders(data);
      } catch {
        if (!cancelled) setError('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api]);

  const shown = (orders ?? []).filter((order) => {
    const submitted = order.result?.status === 'SUBMITTED';
    if ((tab === 'submitted') !== submitted) return false;
    return !onlyMine || order.isPerformer;
  });

  return (
    <div className="space-y-6 pb-10">
      <div>
        <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">Nhập kết quả cận lâm sàng</h1>
        <p className="text-sm text-slate-500 mt-1">Các dịch vụ đã thực hiện, xếp theo thời gian thực hiện sớm nhất.</p>
      </div>

      <div className="flex items-start gap-2.5 p-3 rounded-xl bg-sky-50 border border-sky-200 text-sky-800">
        <Info className="w-4 h-4 shrink-0 mt-0.5" />
        <p className="text-xs">Đang dùng dữ liệu mẫu: chưa có API kết quả cận lâm sàng. Dữ liệu nhập sẽ mất khi tải lại trang.</p>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="Lọc kết quả" className="inline-flex p-1 bg-white border border-slate-200 rounded-xl">
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={tab === item.id}
              onClick={() => setTab(item.id)}
              className={`px-4 py-1.5 text-sm font-semibold rounded-lg cursor-pointer ${tab === item.id ? 'bg-[#127e7a] text-white' : 'text-slate-600 hover:bg-slate-50'}`}
            >
              {item.label}
            </button>
          ))}
        </div>
        <label className="inline-flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
          <input type="checkbox" checked={onlyMine} onChange={(event) => setOnlyMine(event.target.checked)} className="accent-[#127e7a]" />
          Chỉ dịch vụ tôi thực hiện
        </label>
      </div>

      {error && <FormAlert>{error}</FormAlert>}

      <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="bg-slate-50/80 text-xs font-semibold text-slate-500 border-b border-slate-200">
              <th className="px-4 py-3">Mã phiếu</th>
              <th className="px-4 py-3">Bệnh nhân</th>
              <th className="px-4 py-3">Dịch vụ</th>
              <th className="px-4 py-3">Thực hiện lúc</th>
              <th className="px-4 py-3">Người thực hiện</th>
              <th className="px-4 py-3">Trạng thái</th>
              <th className="px-4 py-3 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {orders === null && !error && (
              <tr><td colSpan={7} className="px-4 py-10 text-center text-slate-400">Đang tải…</td></tr>
            )}
            {orders !== null && shown.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-10 text-center text-slate-400">Không có dữ liệu.</td></tr>
            )}
            {shown.map((order) => {
              const status = statusOf(order);
              return (
                <tr key={order.id} className="hover:bg-slate-50/60">
                  <td className="px-4 py-3 font-mono text-xs text-slate-600 whitespace-nowrap">{order.orderCode}</td>
                  <td className="px-4 py-3">
                    <div className="font-semibold text-slate-900 whitespace-nowrap">{order.patient.name}</div>
                    <div className="text-xs text-slate-500">{order.patient.age} tuổi · {order.patient.gender}</div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="font-medium text-slate-800">{order.serviceName}</div>
                    <div className="text-xs text-slate-500">{KIND_LABEL[order.kind]}</div>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-slate-600">{formatDateTime(order.performedAt)}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-slate-600">{order.performer?.name}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2.5 py-0.5 rounded-lg border text-xs font-semibold whitespace-nowrap ${status.className}`}>{status.label}</span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      onClick={() => navigate(`/technician/results/${order.id}`)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-lg cursor-pointer whitespace-nowrap"
                    >
                      <FileEdit className="w-3.5 h-3.5" />
                      {order.isPerformer && order.result?.status !== 'SUBMITTED' ? 'Nhập kết quả' : 'Xem phiếu'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default ParaclinicalResultListPage;
