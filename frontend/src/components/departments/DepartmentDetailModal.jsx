import { useMemo } from 'react';
import {
  X,
  Building2,
  Home,
  MapPin,
  User,
  FileText,
  Clock,
  Activity,
  Layers,
  Sparkles,
  Stethoscope,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';
import { Button } from '../ui/button';
import {
  getServicesForRoom,
  SERVICE_TYPE_MAP,
  SERVICE_GROUP_LABELS,
} from '../../constants/serviceData';
import { formatPrice } from '../services/serviceFormat';

export function DepartmentDetailModal({ isOpen, item, onEdit, onClose }) {
  if (!isOpen || !item) return null;

  const isDept = item.unitType === 'DEPARTMENT';
  const isActive = item.status === 'ACTIVE';

  // Lấy danh sách dịch vụ thực hiện tại phòng
  const roomServices = useMemo(() => {
    if (isDept) return [];
    return getServicesForRoom(item);
  }, [item, isDept]);

  // Thông số kỹ thuật chi tiết của phòng
  const roomSpecs = useMemo(() => {
    if (isDept) return null;
    const isExam = item.type === 'EXAMINATION' || item.code?.startsWith('PK-');
    return {
      operatingHours: '07:30 – 16:30 (Thứ 2 – Thứ 7)',
      capacity: isExam ? '40 – 60 lượt khám/ngày' : '30 – 50 ca xét nghiệm/ngày',
      deviceSetup: isExam
        ? 'Bàn khám tiêu chuẩn, máy đo huyết áp điện tử, ống nghe y tế, đèn khám, giường khám bệnh'
        : item.code?.includes('SA')
        ? 'Hệ thống siêu âm 4D màu Doppler kỹ thuật số cao cấp'
        : item.code?.includes('XQ')
        ? 'Hệ thống máy chụp X-quang kỹ thuật số (DR), buồng chì bảo vệ'
        : item.code?.includes('CT')
        ? 'Hệ thống chụp cắt lớp vi tính CT đa dãy đầu dò thế hệ mới'
        : item.code?.includes('MRI')
        ? 'Hệ thống chụp cộng hưởng từ 1.5 Tesla'
        : item.code?.includes('NS')
        ? 'Hệ thống dàn nội soi dạ dày đại tràng ống mềm độ nét cao (NBI)'
        : item.code?.includes('DT')
        ? 'Máy ghi điện tâm đồ 12 kênh kỹ thuật số'
        : 'Hệ thống máy phân tích tự động, buồng an toàn sinh học cấp II',
    };
  }, [item, isDept]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className={`w-full ${
          isDept ? 'max-w-lg' : 'max-w-3xl'
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
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-[#f8faf9] p-3.5 rounded-xl border border-slate-100">
            <div>
              <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Trạng thái</span>
              <span
                className={`inline-flex items-center px-2 py-0.5 rounded-md font-semibold text-[11px] border ${
                  isActive
                    ? 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
                    : 'bg-[#fde9eb] text-[#d93b4a] border-[#f9cfd4]'
                }`}
              >
                {isActive ? 'Đang hoạt động' : 'Tạm ngưng / Bảo trì'}
              </span>
            </div>
            <div>
              <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Phân loại</span>
              <span className="font-semibold text-slate-800">
                {item.type === 'CLINICAL'
                  ? 'Lâm sàng'
                  : item.type === 'PARACLINICAL'
                  ? 'Cận lâm sàng'
                  : item.type === 'EXAMINATION'
                  ? 'Khám lâm sàng'
                  : item.type === 'ADMINISTRATIVE'
                  ? 'Hành chính / Thu ngân'
                  : item.type || '—'}
              </span>
            </div>
            <div>
              <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Khoa chủ quản</span>
              <span className="font-semibold text-slate-800 line-clamp-1">
                {isDept ? item.name : item.departmentName || '—'}
              </span>
            </div>
            <div>
              <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Vị trí / Tầng</span>
              <span className="font-semibold text-slate-800 line-clamp-1">
                {item.location || '—'}
              </span>
            </div>
          </div>

          {/* Nếu là Chuyên khoa (DEPARTMENT) */}
          {isDept && (
            <div className="space-y-3 pt-1">
              <div className="flex items-start gap-2.5 text-slate-700">
                <User className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span className="font-semibold text-slate-500 block text-[11px]">Trưởng khoa</span>
                  <span className="font-medium text-slate-800">{item.headPerson || 'Chưa bổ nhiệm'}</span>
                </div>
              </div>

              <div className="flex items-start gap-2.5 text-slate-700">
                <Home className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span className="font-semibold text-slate-500 block text-[11px]">Số lượng phòng</span>
                  <span className="font-medium text-slate-800">{item.roomCount || 0} phòng trực thuộc</span>
                </div>
              </div>

              {item.description && (
                <div className="flex items-start gap-2.5 text-slate-700 pt-1">
                  <FileText className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
                  <div className="flex-1">
                    <span className="font-semibold text-slate-500 block text-[11px]">Mô tả nhiệm vụ</span>
                    <p className="text-slate-600 leading-relaxed mt-0.5">{item.description}</p>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Nếu là Phòng chức năng (ROOM) */}
          {!isDept && (
            <div className="space-y-4">
              {/* Thẻ thông số chi tiết của phòng */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="p-3 bg-white rounded-xl border border-slate-200/90 shadow-2xs space-y-1.5">
                  <div className="flex items-center gap-1.5 text-slate-600 font-semibold text-[11px]">
                    <Clock className="w-3.5 h-3.5 text-[#0d9488]" />
                    <span>Thời gian tiếp nhận</span>
                  </div>
                  <p className="text-slate-800 font-medium">{roomSpecs?.operatingHours}</p>
                </div>

                <div className="p-3 bg-white rounded-xl border border-slate-200/90 shadow-2xs space-y-1.5">
                  <div className="flex items-center gap-1.5 text-slate-600 font-semibold text-[11px]">
                    <Activity className="w-3.5 h-3.5 text-[#0d9488]" />
                    <span>Công suất phục vụ dự kiến</span>
                  </div>
                  <p className="text-slate-800 font-medium">{roomSpecs?.capacity}</p>
                </div>
              </div>

              {/* Trang thiết bị buồng phòng */}
              <div className="p-3.5 bg-slate-50/70 rounded-xl border border-slate-200/80 space-y-1">
                <div className="flex items-center gap-1.5 text-slate-700 font-bold text-[11px]">
                  <Layers className="w-3.5 h-3.5 text-[#0d9488]" />
                  <span>Cơ sở vật chất & Trang thiết bị buồng phòng</span>
                </div>
                <p className="text-slate-600 text-xs leading-relaxed">{roomSpecs?.deviceSetup}</p>
              </div>

              {/* Bảng danh sách các dịch vụ y tế được phòng khám phục vụ */}
              <div className="space-y-2 pt-1">
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
              onEdit?.(item);
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
