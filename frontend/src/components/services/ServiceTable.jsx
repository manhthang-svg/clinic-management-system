import { Power, PowerOff } from 'lucide-react';
import {
  SERVICE_TYPE_MAP,
  SERVICE_GROUP_LABELS,
  SERVICE_STATUS_LABELS,
} from '../../constants/serviceData';
import { formatPrice } from './serviceFormat';

const STATUS_TONES = {
  ACTIVE: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]',
  INACTIVE: 'bg-slate-100 text-slate-500 border-slate-200',
};

const ACTION_CLASS = 'p-1.5 rounded-lg transition-colors cursor-pointer';

export function ServiceTable({ services, totalCount, onToggleStatus }) {
  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-[11px] font-semibold text-[#1b2533]">
              <th className="pl-3 pr-2 py-3">Mã DV</th>
              <th className="px-2 py-3">Tên dịch vụ</th>
              <th className="px-2 py-3">Loại</th>
              <th className="px-2 py-3">Nhóm / Khoa thực hiện</th>
              <th className="px-2 py-3 text-right">Giá viện phí</th>
              <th className="px-2 py-3 text-right">Giá BHYT</th>
              <th className="px-2 py-3">Trạng thái</th>
              <th className="px-2 py-3">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-[11px]">
            {services.length === 0 ? (
              <tr>
                <td colSpan="8" className="p-10 text-center text-slate-400">
                  {totalCount === 0 ? 'Chưa có dịch vụ nào.' : 'Không tìm thấy dịch vụ phù hợp.'}
                </td>
              </tr>
            ) : (
              services.map((service) => {
                const type = SERVICE_TYPE_MAP[service.type];
                const active = service.status === 'ACTIVE';
                const ToggleIcon = active ? PowerOff : Power;
                return (
                  <tr key={service.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="pl-3 pr-2 py-3 font-mono text-[10px] text-slate-500 whitespace-nowrap">{service.code}</td>
                    <td className="px-2 py-3 font-semibold text-slate-800 min-w-[160px]">{service.name}</td>
                    <td className="px-2 py-3">
                      <span className={`inline-block px-2 py-0.5 rounded-md text-[10px] font-semibold border whitespace-nowrap ${type?.color}`}>
                        {type?.label}
                      </span>
                    </td>
                    <td className="px-2 py-3 min-w-[120px]">
                      <div className="font-medium text-slate-700">
                        {service.group ? SERVICE_GROUP_LABELS[service.group] : type?.label}
                      </div>
                      <div className="text-[10px] text-slate-400">{service.department || 'Dùng chung các phòng khám'}</div>
                    </td>
                    <td className="px-2 py-3 text-right font-semibold text-slate-700 whitespace-nowrap">
                      {formatPrice(service.clinicPrice)}
                    </td>
                    <td className="px-2 py-3 text-right whitespace-nowrap">
                      {service.insurancePrice === null ? (
                        <span className="text-slate-400">Không áp dụng</span>
                      ) : (
                        <span className="font-semibold text-slate-700">{formatPrice(service.insurancePrice)}</span>
                      )}
                    </td>
                    <td className="px-2 py-3">
                      <span className={`inline-block px-2 py-0.5 rounded-md text-[10px] font-semibold border whitespace-nowrap ${STATUS_TONES[service.status]}`}>
                        {SERVICE_STATUS_LABELS[service.status]}
                      </span>
                    </td>
                    <td className="px-2 py-3">
                      <button
                        type="button"
                        title={active ? 'Ngừng hoạt động' : 'Kích hoạt'}
                        aria-label={`${active ? 'Ngừng hoạt động' : 'Kích hoạt'} ${service.name}`}
                        onClick={() => onToggleStatus?.(service)}
                        className={`${ACTION_CLASS} ${
                          active ? 'text-slate-500 hover:bg-rose-50 hover:text-rose-600' : 'text-slate-500 hover:bg-teal-50 hover:text-[#127e7a]'
                        }`}
                      >
                        <ToggleIcon className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <p className="text-[11px] text-slate-500">
        Hiển thị {services.length} / {totalCount} dịch vụ · Giá tính theo VNĐ
      </p>
    </div>
  );
}

export default ServiceTable;
