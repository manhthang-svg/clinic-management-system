import { X, Building2, Home, MapPin, User, ShieldCheck, FileText, Calendar } from 'lucide-react';
import { Button } from '../ui/button';

export function DepartmentDetailModal({ isOpen, item, onEdit, onClose }) {
  if (!isOpen || !item) return null;

  const isDept = item.unitType === 'DEPARTMENT';
  const isActive = item.status === 'ACTIVE';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl border border-[#e3ece9] transition-all transform scale-100"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-start justify-between pb-4 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <div
              className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${
                isDept ? 'bg-blue-50 text-blue-600' : 'bg-teal-50 text-[#0d9488]'
              }`}
            >
              {isDept ? <Building2 className="w-6 h-6" /> : <Home className="w-6 h-6" />}
            </div>
            <div>
              <div className="flex items-center gap-2">
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

        {/* Content list */}
        <div className="py-4 space-y-3.5 text-xs">
          <div className="grid grid-cols-2 gap-4 bg-[#f8faf9] p-3.5 rounded-xl border border-slate-100">
            <div>
              <span className="text-[11px] font-medium text-slate-400 block mb-0.5">Trạng thái</span>
              <span
                className={`inline-flex items-center px-2.5 py-0.5 rounded-md font-semibold border ${
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
                  ? 'Khám bệnh'
                  : item.type || '—'}
              </span>
            </div>
          </div>

          <div className="space-y-2.5 pt-1">
            {!isDept && (
              <div className="flex items-start gap-2.5 text-slate-700">
                <Building2 className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span className="font-semibold text-slate-500 block text-[11px]">Khoa trực thuộc</span>
                  <span className="font-medium text-slate-800">{item.departmentName || '—'}</span>
                </div>
              </div>
            )}

            <div className="flex items-start gap-2.5 text-slate-700">
              <MapPin className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
              <div className="flex-1">
                <span className="font-semibold text-slate-500 block text-[11px]">Vị trí</span>
                <span className="font-medium text-slate-800">{item.location || '—'}</span>
              </div>
            </div>

            <div className="flex items-start gap-2.5 text-slate-700">
              <User className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
              <div className="flex-1">
                <span className="font-semibold text-slate-500 block text-[11px]">
                  {isDept ? 'Trưởng khoa' : 'Người phụ trách'}
                </span>
                <span className="font-medium text-slate-800">{item.headPerson || 'Chưa phân công'}</span>
              </div>
            </div>

            {isDept && (
              <div className="flex items-start gap-2.5 text-slate-700">
                <Home className="w-4 h-4 text-[#127e7a] shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span className="font-semibold text-slate-500 block text-[11px]">Số lượng phòng</span>
                  <span className="font-medium text-slate-800">{item.roomCount || 0} phòng trực thuộc</span>
                </div>
              </div>
            )}

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
        </div>

        {/* Footer buttons */}
        <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-2.5">
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
