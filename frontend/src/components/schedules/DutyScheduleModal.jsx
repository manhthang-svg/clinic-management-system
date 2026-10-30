import { useState } from 'react';
import { X, Sun, Sunset, User, DoorOpen, Calendar } from 'lucide-react';
import { Button } from '../ui/button';

function formatDisplayDate(dateStr) {
  if (!dateStr) return '';
  const parts = dateStr.split('-');
  if (parts.length !== 3) return dateStr;
  const [y, m, d] = parts;
  const dateObj = new Date(parseInt(y, 10), parseInt(m, 10) - 1, parseInt(d, 10));
  const dayNames = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
  const dayName = dayNames[dateObj.getDay()] || '';
  return `${dayName}, ${d}/${m}/${y}`;
}

function buildInitialFormData({ initialData, weekStartDate, staffList }) {
  if (initialData) {
    return {
      id: initialData.id || null,
      workDate: initialData.workDate || '',
      shift: initialData.shift || 'MORNING',
      staffAccountId: initialData.staffAccountId || '',
      roomId: initialData.roomId || '',
      notes: initialData.notes || '',
    };
  }
  const todayStr = new Date().toISOString().split('T')[0];
  const defaultDate = weekStartDate
    ? (new Date(weekStartDate).toISOString().split('T')[0])
    : todayStr;

  return {
    id: null,
    workDate: defaultDate,
    shift: 'MORNING',
    staffAccountId: staffList[0]?.id || '',
    roomId: '',
    notes: '',
  };
}

export function DutyScheduleModal(props) {
  if (!props.isOpen) return null;
  return (
    <DutyScheduleModalContent
      key={`${props.initialData?.id || 'new'}-${props.weekStartDate || ''}-${props.initialData?.shift || ''}`}
      {...props}
    />
  );
}

function DutyScheduleModalContent({
  onClose,
  onSave,
  initialData = null,
  weekStartDate,
  staffList = [],
  roomList = [],
}) {
  const [formData, setFormData] = useState(() =>
    buildInitialFormData({ initialData, weekStartDate, staffList })
  );

  const todayStr = new Date().toISOString().split('T')[0];
  const isPast = Boolean(formData.workDate && formData.workDate < todayStr);

  const [errors, setErrors] = useState({});

  const validate = () => {
    const errs = {};
    if (isPast) {
      errs.general = 'Không thể phân công hoặc chỉnh sửa lịch trực trong quá khứ';
    }
    if (!formData.staffAccountId) errs.staffAccountId = 'Vui lòng chọn nhân viên trực';
    if (!formData.roomId) errs.roomId = 'Vui lòng chọn phòng làm việc';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    const selectedStaff = staffList.find(s => s.id === formData.staffAccountId);
    const selectedRoom = roomList.find(r => r.id === formData.roomId);

    onSave({
      ...formData,
      id: formData.id || undefined,
      roomId: formData.roomId ? formData.roomId : null,
      notes: formData.notes || '',
      staffName: selectedStaff?.fullName || selectedStaff?.name || 'Nhân viên',
      staffRole: selectedStaff?.role || 'STAFF',
      roomName: selectedRoom?.name || selectedRoom?.roomName || null,
      roomCode: selectedRoom?.code || selectedRoom?.roomCode || null,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className="w-full max-w-lg rounded-2xl bg-white shadow-2xl border border-slate-100 overflow-hidden transform transition-all"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/60">
          <div>
            <h3 className="text-lg font-bold text-slate-800">
              {formData.id ? 'Chỉnh sửa ca trực' : 'Phân công ca trực mới'}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Chọn nhân viên và phòng làm việc cho ca trực đã chọn
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Cố định Ca trực & Ngày trực đã chọn ở bảng ngoài */}
          <div className={`p-4 rounded-2xl border flex items-center justify-between transition-all ${
            formData.shift === 'MORNING'
              ? 'bg-amber-50/70 border-amber-200/90 text-amber-950'
              : 'bg-sky-50/70 border-sky-200/90 text-sky-950'
          }`}>
            <div className="flex items-center gap-3">
              <div className={`p-2.5 rounded-xl ${
                formData.shift === 'MORNING'
                  ? 'bg-amber-100 text-amber-700 shadow-2xs'
                  : 'bg-sky-100 text-sky-700 shadow-2xs'
              }`}>
                {formData.shift === 'MORNING' ? (
                  <Sun className="w-5 h-5" />
                ) : (
                  <Sunset className="w-5 h-5" />
                )}
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold">
                    {formData.shift === 'MORNING' ? 'Ca Sáng' : 'Ca Chiều'}
                  </span>
                  <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-white/90 border border-slate-200/80 text-slate-600">
                    {formData.shift === 'MORNING' ? '08:00 - 11:30' : '13:00 - 17:30'}
                  </span>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-600 mt-1 font-medium">
                  <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>Ngày trực: <strong className="text-slate-800">{formatDisplayDate(formData.workDate)}</strong></span>
                </div>
              </div>
            </div>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-lg bg-white/95 border border-slate-200/80 text-slate-500 shadow-2xs">
              Cố định
            </span>
          </div>

          {/* Staff Member (Dropdown) */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Nhân viên phụ trách ca <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <User className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
              <select
                value={formData.staffAccountId}
                onChange={e => setFormData(prev => ({ ...prev, staffAccountId: e.target.value }))}
                className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm text-slate-800 bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all cursor-pointer ${
                  errors.staffAccountId ? 'border-rose-400 bg-rose-50/20' : 'border-slate-200'
                }`}
              >
                <option value="">-- Chọn nhân viên --</option>
                {staffList.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.fullName || s.name} ({s.role || 'Nhân viên'})
                  </option>
                ))}
              </select>
            </div>
            {errors.staffAccountId && (
              <p className="text-xs text-rose-500 mt-1">{errors.staffAccountId}</p>
            )}
          </div>

          {/* Room (Dropdown, Required) */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Phòng làm việc <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <DoorOpen className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
              <select
                value={formData.roomId || ''}
                onChange={e => setFormData(prev => ({ ...prev, roomId: e.target.value }))}
                className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm text-slate-800 bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all cursor-pointer ${
                  errors.roomId ? 'border-rose-400 bg-rose-50/20' : 'border-slate-200'
                }`}
              >
                <option value="">-- Chọn phòng làm việc --</option>
                {Object.entries(
                  roomList.reduce((acc, r) => {
                    const group = r.departmentName || 'Phòng khám & chức năng';
                    if (!acc[group]) acc[group] = [];
                    acc[group].push(r);
                    return acc;
                  }, {})
                ).map(([groupName, roomsInGroup]) => (
                  <optgroup key={groupName} label={groupName}>
                    {roomsInGroup.map(r => (
                      <option key={r.id} value={r.id}>
                        {r.code ? `[${r.code}] ` : ''}{r.name || r.roomName} {r.location ? `(${r.location})` : ''}
                        {r.status === 'INACTIVE' ? ' [Tạm ngưng]' : ''}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>
            {errors.roomId && (
              <p className="text-xs text-rose-500 mt-1">{errors.roomId}</p>
            )}
          </div>

          {/* Warning banner if in the past */}
          {isPast && (
            <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-center gap-2">
              <span className="font-semibold">Lưu ý:</span>
              <span>Lịch trực này thuộc ngày trong quá khứ nên chỉ có thể xem, không thể chỉnh sửa.</span>
            </div>
          )}

          {/* Actions */}
          <div className="pt-3 flex items-center justify-end gap-3 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
              className="px-4 text-xs font-semibold cursor-pointer"
            >
              Đóng
            </Button>
            {!isPast && (
              <Button
                type="submit"
                variant="mint"
                size="sm"
                className="px-5 text-xs font-semibold shadow-sm shadow-teal-500/20 cursor-pointer"
              >
                {formData.id ? 'Cập nhật phân công' : 'Lưu phân công'}
              </Button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}

export default DutyScheduleModal;

