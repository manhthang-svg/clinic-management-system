import { useMemo, useState, useEffect } from 'react';
import {
  X,
  Building2,
  Home,
  MapPin,
  FileText,
  Stethoscope,
  CheckCircle2,
  DoorOpen,
  Sun,
  Sunset,
  CalendarDays,
} from 'lucide-react';
import { Button } from '../ui/button';
import { useDepartmentServices } from '../../hooks';
import {
  SERVICE_TYPE_MAP,
  SERVICE_GROUP_LABELS,
} from '../../constants/serviceData';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { formatPrice } from '../services/serviceFormat';
import { describeApiError } from '../../utils/describeApiError';
import { getVietnamTodayStr } from '../../utils/vietnamTimeUtils';

const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  DOCTOR: 'Bác sĩ',
  TECHNICIAN: 'Kỹ thuật viên',
  NURSE: 'Điều dưỡng',
  RECEPTIONIST: 'Tiếp đón',
  CASHIER: 'Thu ngân',
};

const ROLE_COLORS = {
  DOCTOR: 'bg-teal-50 text-teal-700 border-teal-200',
  TECHNICIAN: 'bg-purple-50 text-purple-700 border-purple-200',
  NURSE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  ADMIN: 'bg-blue-50 text-blue-700 border-blue-200',
};

export function DepartmentDetailModal(props) {
  if (!props.isOpen || !props.item) return null;
  return <DepartmentDetailModalContent {...props} />;
}

function DepartmentDetailModalContent({ item, rooms = [], onEdit, onClose }) {
  const isDept = item.unitType === 'DEPARTMENT';
  const isActive = item.status === 'ACTIVE';

  // Lấy lịch trực thực tế hôm nay của phòng từ phân ca trực backend (không mock fallback)
  const [roomDuties, setRoomDuties] = useState({ morning: [], afternoon: [], loading: false, error: null });

  useEffect(() => {
    if (isDept || !item?.id) return;
    let isMounted = true;
    const todayStr = getVietnamTodayStr();

    async function loadTodayRoomDuties() {
      setRoomDuties(prev => ({ ...prev, loading: true, error: null }));
      try {
        const res = await dutyScheduleApi.getWeeklyScheduleByRoom(item.id, todayStr, todayStr);
        const duties = res?.items || [];
        if (isMounted) {
          setRoomDuties({
            morning: duties.filter(d => d.shift === 'MORNING'),
            afternoon: duties.filter(d => d.shift === 'AFTERNOON'),
            loading: false,
            error: null,
          });
        }
      } catch (err) {
        console.error('Không thể tải ca trực hôm nay của phòng:', err);
        if (isMounted) {
          setRoomDuties({ morning: [], afternoon: [], loading: false, error: err });
        }
      }
    }

    loadTodayRoomDuties();
    return () => { isMounted = false; };
  }, [isDept, item?.id]);

  // Lấy danh sách các phòng trực thuộc chuyên khoa này từ props truyền vào (dữ liệu thật từ API)
  const departmentRooms = useMemo(() => {
    if (!isDept) return [];
    const sourceRooms = Array.isArray(rooms) ? rooms : [];

    return sourceRooms.filter((r) => {
      return (
        r.departmentId === item.id ||
        r.departmentId === item.departmentId ||
        r.departmentName === item.name ||
        (item.code && r.departmentCode === item.code)
      );
    });
  }, [isDept, item, rooms]);

  // Tự động tải dịch vụ thực tế từ backend nếu item chưa có sẵn assignedServices
  const needsFetchServices = !isDept && (!item.assignedServices || item.assignedServices.length === 0);
  const { services: fetchedServices } = useDepartmentServices(
    needsFetchServices
      ? {
          departmentId: item.departmentId,
          serviceGroupId: item.serviceGroupId,
          roomType: item.type,
        }
      : null
  );

  // Danh sách dịch vụ y tế được gán cho phòng khám
  const roomServices = useMemo(() => {
    if (isDept) return [];
    if (Array.isArray(item.assignedServices) && item.assignedServices.length > 0) {
      return item.assignedServices;
    }
    if (Array.isArray(fetchedServices) && fetchedServices.length > 0) {
      return fetchedServices;
    }
    return [];
  }, [isDept, item.assignedServices, fetchedServices]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className={`w-full ${
          isDept ? 'max-w-2xl' : 'max-w-3xl'
        } rounded-2xl bg-white p-5 sm:p-6 shadow-2xl border border-[#e3ece9] transition-all transform scale-100 max-h-[92vh] flex flex-col`}
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-start justify-between pb-4 border-b border-slate-100 shrink-0">
          <div className="flex items-center gap-3">
            <div
              className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${
                isDept ? 'bg-blue-50 text-blue-600' : 'bg-teal-50 text-[#0d9488]'
              }`}
            >
              {isDept ? <Building2 className="w-6 h-6" /> : <Home className="w-6 h-6" />}
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h3 className="text-lg font-bold text-[#142534] tracking-tight">{item.name}</h3>
                <span
                  className={`inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold border ${
                    isDept
                      ? 'bg-blue-50 text-blue-700 border-blue-200'
                      : 'bg-teal-50 text-[#0d9488] border-teal-200'
                  }`}
                >
                  {isDept ? 'Khoa chuyên môn' : 'Phòng chức năng'}
                </span>
              </div>
              <p className="text-xs font-mono text-slate-500 mt-0.5">Mã đơn vị: {item.code}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body with scroll */}
        <div className="py-4 space-y-4 text-xs overflow-y-auto flex-1 pr-1">
          {/* Thông tin tổng quan trạng thái & phân loại */}
          {isDept ? (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 bg-[#f8faf9] p-3.5 rounded-xl border border-slate-100">
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Trạng thái</span>
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded-md font-semibold text-[11px] border ${
                    isActive
                      ? 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
                      : 'bg-[#fde9eb] text-[#d93b4a] border-[#f9cfd4]'
                  }`}
                >
                  {isActive ? 'Đang hoạt động' : 'Đang khóa'}
                </span>
              </div>
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Phân loại</span>
                <span className="font-semibold text-slate-800">
                  {item.type === 'CLINICAL' ? 'Lâm sàng' : 'Cận lâm sàng'}
                </span>
              </div>
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Mô tả nhiệm vụ</span>
                <span className="font-semibold text-slate-800 line-clamp-2" title={item.description}>
                  {item.description || 'Chưa có mô tả'}
                </span>
              </div>
            </div>
          ) : (
            <div className={`grid grid-cols-2 ${item.serviceGroupName ? 'sm:grid-cols-5' : 'sm:grid-cols-4'} gap-3 bg-[#f8faf9] p-3.5 rounded-xl border border-slate-100`}>
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Trạng thái</span>
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded-md font-semibold text-[11px] border ${
                    isActive
                      ? 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
                      : 'bg-[#fde9eb] text-[#d93b4a] border-[#f9cfd4]'
                  }`}
                >
                  {isActive ? 'Đang hoạt động' : 'Tạm ngưng'}
                </span>
              </div>
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Phân loại</span>
                <span className="font-semibold text-slate-800">
                  {item.type === 'EXAMINATION'
                    ? 'Khám lâm sàng'
                    : item.type === 'PARACLINICAL'
                    ? 'Cận lâm sàng'
                    : item.type === 'ADMINISTRATIVE'
                    ? 'Hành chính / Thu ngân'
                    : item.type || '—'}
                </span>
              </div>
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Khoa chủ quản</span>
                <span className="font-semibold text-slate-800 line-clamp-1">
                  {item.departmentName || '—'}
                </span>
              </div>
              {item.serviceGroupName && (
                <div>
                  <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Nhóm kỹ thuật</span>
                  <span className="font-semibold text-[#0d9488] line-clamp-1" title={item.serviceGroupName}>
                    {item.serviceGroupName}
                  </span>
                </div>
              )}
              <div>
                <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Vị trí / Tầng</span>
                <span className="font-semibold text-slate-800 line-clamp-1">
                  {item.location || '—'}
                </span>
              </div>
            </div>
          )}

          {/* Nếu là Chuyên khoa (DEPARTMENT) */}
          {isDept && (
            <div className="space-y-3.5 pt-1">
              {/* Danh sách phòng chức năng trực thuộc khoa */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
                    <DoorOpen className="w-4 h-4 text-[#0d9488]" />
                    <span>Danh sách phòng chức năng trực thuộc</span>
                    <span className="ml-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/70">
                      {departmentRooms.length} phòng
                    </span>
                  </div>
                </div>

                {departmentRooms.length === 0 ? (
                  <div className="p-4 rounded-xl border border-dashed border-slate-200 text-center text-slate-400 text-xs">
                    Chuyên khoa này hiện chưa có phòng chức năng nào trực thuộc.
                  </div>
                ) : (
                  <div className="overflow-hidden rounded-xl border border-slate-200/80 bg-white">
                    <table className="w-full text-left text-[11px] border-collapse">
                      <thead>
                        <tr className="bg-slate-50/80 border-b border-slate-200/80 text-slate-700 font-semibold">
                          <th className="py-2.5 px-3">Mã phòng</th>
                          <th className="py-2.5 px-3">Tên phòng</th>
                          <th className="py-2.5 px-3">Vị trí buồng</th>
                          <th className="py-2.5 px-3 text-right">Trạng thái</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {departmentRooms.map((room) => {
                          const isRoomActive = room.status === 'ACTIVE';
                          return (
                            <tr key={room.id} className="hover:bg-slate-50/70 transition-colors">
                              <td className="py-2.5 px-3 font-mono font-bold text-slate-700 whitespace-nowrap">
                                {room.code}
                              </td>
                              <td className="py-2.5 px-3 font-semibold text-slate-800">
                                {room.name}
                              </td>
                              <td className="py-2.5 px-3 text-slate-500 whitespace-nowrap">
                                {room.location || '—'}
                              </td>
                              <td className="py-2.5 px-3 text-right whitespace-nowrap">
                                <span
                                  className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold border ${
                                    isRoomActive
                                      ? 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
                                      : 'bg-[#fde9eb] text-[#d93b4a] border-[#f9cfd4]'
                                  }`}
                                >
                                  {isRoomActive ? 'Đang hoạt động' : 'Tạm ngưng'}
                                </span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Nếu là Phòng chức năng (ROOM) */}
          {!isDept && (
            <div className="space-y-4">
              {/* Bác sĩ trực tại phòng (Ca Sáng & Ca Chiều) */}
              <div className="space-y-2 pt-1">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-md bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
                      <CalendarDays className="w-3.5 h-3.5" />
                    </div>
                    <h4 className="text-xs font-bold text-slate-800 tracking-tight">
                      Bác sĩ / Kỹ thuật viên trực phòng (Hôm nay)
                    </h4>
                  </div>
                  <span className="text-[10.5px] text-teal-700 font-semibold bg-teal-50 px-2 py-0.5 rounded-full border border-teal-100">
                    Theo lịch trực tuần
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {/* Ca Sáng */}
                  <div className="p-3 rounded-xl bg-amber-50/50 border border-amber-200/80 flex items-start gap-2.5">
                    <div className="p-2 rounded-lg bg-amber-100 text-amber-700 shrink-0">
                      <Sun className="w-4 h-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <span className="text-[10px] font-bold text-amber-900 uppercase tracking-wider block">
                        Ca Sáng (08:00 - 11:30)
                      </span>
                      {roomDuties.loading ? (
                        <span className="text-xs text-slate-400 italic block mt-0.5">
                          Đang tải lịch trực...
                        </span>
                      ) : roomDuties.error ? (
                        <span className="text-[11px] text-rose-500 font-medium block mt-0.5">
                          {describeApiError(roomDuties.error, 'Lỗi tải')}
                        </span>
                      ) : roomDuties.morning.length > 0 ? (
                        <div className="space-y-1.5 mt-1">
                          {roomDuties.morning.map((duty, idx) => (
                            <div key={duty.id || idx} className="flex items-center justify-between gap-1">
                              <span className="text-xs font-bold text-slate-800 truncate" title={duty.staffName}>
                                {duty.staffName}
                              </span>
                              <span
                                className={`text-[9.5px] px-1.5 py-0.2 rounded border font-medium shrink-0 ${
                                  ROLE_COLORS[duty.staffRole] || 'bg-slate-50 text-slate-600 border-slate-200'
                                }`}
                              >
                                {ROLE_LABELS[duty.staffRole] || duty.staffRole || 'Nhân sự'}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400 italic block mt-0.5">
                          Chưa phân công trực
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Ca Chiều */}
                  <div className="p-3 rounded-xl bg-sky-50/50 border border-sky-200/80 flex items-start gap-2.5">
                    <div className="p-2 rounded-lg bg-sky-100 text-sky-700 shrink-0">
                      <Sunset className="w-4 h-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <span className="text-[10px] font-bold text-sky-900 uppercase tracking-wider block">
                        Ca Chiều (13:00 - 17:30)
                      </span>
                      {roomDuties.loading ? (
                        <span className="text-xs text-slate-400 italic block mt-0.5">
                          Đang tải lịch trực...
                        </span>
                      ) : roomDuties.error ? (
                        <span className="text-[11px] text-rose-500 font-medium block mt-0.5">
                          {describeApiError(roomDuties.error, 'Lỗi tải')}
                        </span>
                      ) : roomDuties.afternoon.length > 0 ? (
                        <div className="space-y-1.5 mt-1">
                          {roomDuties.afternoon.map((duty, idx) => (
                            <div key={duty.id || idx} className="flex items-center justify-between gap-1">
                              <span className="text-xs font-bold text-slate-800 truncate" title={duty.staffName}>
                                {duty.staffName}
                              </span>
                              <span
                                className={`text-[9.5px] px-1.5 py-0.2 rounded border font-medium shrink-0 ${
                                  ROLE_COLORS[duty.staffRole] || 'bg-slate-50 text-slate-600 border-slate-200'
                                }`}
                              >
                                {ROLE_LABELS[duty.staffRole] || duty.staffRole || 'Nhân sự'}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400 italic block mt-0.5">
                          Chưa phân công trực
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* Bảng danh sách các dịch vụ y tế được phòng khám phục vụ */}
              <div className="space-y-2 pt-1 border-t border-slate-100">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-md bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
                      <Stethoscope className="w-3.5 h-3.5" />
                    </div>
                    <h4 className="text-xs font-bold text-slate-800 tracking-tight">
                      Danh mục dịch vụ y tế phục vụ tại phòng ({roomServices.length})
                    </h4>
                  </div>
                  <span className="text-[10px] text-slate-400">Đơn vị: VNĐ</span>
                </div>

                <div className="overflow-x-auto rounded-xl border border-slate-200/90 bg-white">
                  <table className="w-full text-left border-collapse text-[11px]">
                    <thead>
                      <tr className="bg-[#f6f8f9] text-[10.5px] font-bold text-[#1b2533] border-b border-slate-200/80">
                        <th className="px-3 py-2.5">Mã DV</th>
                        <th className="px-3 py-2.5">Tên dịch vụ</th>
                        <th className="px-2 py-2.5">Phân loại</th>
                        <th className="px-3 py-2.5 text-right">Viện phí</th>
                        <th className="px-3 py-2.5 text-right">BHYT chi trả</th>
                        <th className="px-2.5 py-2.5 text-center">Trạng thái</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {roomServices.length === 0 ? (
                        <tr>
                          <td colSpan="6" className="p-6 text-center text-slate-400">
                            Chưa có dịch vụ nào liên kết với phòng khám này.
                          </td>
                        </tr>
                      ) : (
                        roomServices.map((service) => {
                          const typeInfo = SERVICE_TYPE_MAP[service.type];
                          return (
                            <tr key={service.code} className="hover:bg-slate-50/80 transition-colors">
                              <td className="px-3 py-2.5 font-mono font-semibold text-slate-700 whitespace-nowrap">
                                {service.code}
                              </td>
                              <td className="px-3 py-2.5 font-medium text-slate-800">
                                <div>{service.name}</div>
                                {service.estimatedMinutes && (
                                  <div className="text-[10px] text-slate-400 mt-0.5">
                                    Thời gian chuẩn: ~{service.estimatedMinutes} phút
                                  </div>
                                )}
                              </td>
                              <td className="px-2 py-2.5 whitespace-nowrap">
                                <span
                                  className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold border ${typeInfo?.color}`}
                                >
                                  {service.group ? SERVICE_GROUP_LABELS[service.group] : typeInfo?.label}
                                </span>
                              </td>
                              <td className="px-3 py-2.5 text-right font-bold text-slate-800 whitespace-nowrap">
                                {formatPrice(service.clinicPrice)} đ
                              </td>
                              <td className="px-3 py-2.5 text-right whitespace-nowrap">
                                {service.insurancePrice ? (
                                  <span className="font-semibold text-emerald-700">
                                    {formatPrice(service.insurancePrice)} đ
                                  </span>
                                ) : (
                                  <span className="text-slate-400 italic">Không hỗ trợ</span>
                                )}
                              </td>
                              <td className="px-2.5 py-2.5 text-center whitespace-nowrap">
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                                  <CheckCircle2 className="w-2.5 h-2.5" />
                                  <span>Sẵn sàng</span>
                                </span>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer buttons */}
        <div className="pt-3.5 border-t border-slate-100 flex items-center justify-end gap-2.5 shrink-0">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            Đóng
          </Button>
          <Button
            type="button"
            variant="mint"
            size="sm"
            onClick={() => {
              onClose();
              onEdit?.({
                ...item,
                serviceGroupId: item.serviceGroupId ?? null,
                serviceGroupName: item.serviceGroupName ?? null,
                assignedServices: roomServices,
              });
            }}
          >
            Chỉnh sửa
          </Button>
        </div>
      </div>
    </div>
  );
}

export default DepartmentDetailModal;
