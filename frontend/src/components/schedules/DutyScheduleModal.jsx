import { useState, useMemo } from 'react';
import { X, Sun, Sunset, User, DoorOpen, Calendar, Link2, AlertCircle, CheckCircle2, Trash2, Eye } from 'lucide-react';
import { Button } from '../ui/button';
import { getVietnamTodayStr, isShiftPast } from '../../utils/vietnamTimeUtils';

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

function categorizeStaff({ roomId, roomObj, staffList = [] }) {
  if (!roomId && !roomObj) {
    return {
      availableStaff: [],
      isPrimary: false,
      roleName: '',
      isParaclinical: false,
    };
  }

  const roomCode = (roomObj?.code || roomObj?.roomCode || '').toUpperCase();
  const roomName = (roomObj?.name || roomObj?.roomName || '').toLowerCase();
  const deptName = (roomObj?.departmentName || '').toLowerCase();
  const roomType = (roomObj?.type || '').toUpperCase();

  // Xác định phòng có phải Cận lâm sàng / Kỹ thuật / Chẩn đoán hình ảnh
  const isParaclinical =
    roomType === 'PARACLINICAL' ||
    deptName.includes('hình ảnh') ||
    deptName.includes('chẩn đoán') ||
    deptName.includes('xét nghiệm') ||
    deptName.includes('cận lâm sàng') ||
    roomCode.startsWith('CDHA') ||
    roomCode.startsWith('XN') ||
    roomCode.startsWith('CLS');

  const requiredRole = isParaclinical ? 'TECHNICIAN' : 'DOCTOR';
  const roleName = isParaclinical ? 'Kỹ thuật viên' : 'Bác sĩ';

  // Lọc nghiêm ngặt: Phòng khám lâm sàng/nội CHỈ LẤY BÁC SĨ (DOCTOR), phòng Cận lâm sàng CHỈ LẤY KTV (TECHNICIAN)
  const roleFilteredStaff = staffList.filter(s => s.role === requiredRole);

  // CHỈ LẤY NHÂN VIÊN ĐƯỢC LIÊN KẾT VỚI PHÒNG ĐÓ TRONG DB (s.roomId === roomId hoặc mã phòng / tên phòng khớp)
  // KHÔNG FALLBACK SANG NHÂN VIÊN NGOÀI PHÒNG
  const assignedStaff = roleFilteredStaff.filter(s => {
    return (
      (s.roomId && s.roomId === roomId) ||
      (roomCode && s.roomCode && s.roomCode.toUpperCase() === roomCode) ||
      (roomName && s.roomName && s.roomName.toLowerCase() === roomName)
    );
  });

  return {
    availableStaff: assignedStaff,
    isPrimary: true,
    isParaclinical,
    roleName,
  };
}

function buildInitialFormData({ initialData, weekStartDate, staffList, roomList, existingSchedules }) {
  const todayStr = getVietnamTodayStr();
  const defaultDate = weekStartDate
    ? (typeof weekStartDate === 'string' ? weekStartDate.slice(0, 10) : todayStr)
    : todayStr;

  if (initialData) {
    let roomId = initialData.roomId || '';
    let staffAccountId = initialData.staffAccountId || '';

    const roomObj = roomList.find(r => r.id === roomId);
    const { availableStaff } = categorizeStaff({
      roomId,
      roomObj,
      staffList,
      existingSchedules,
    });

    // Chỉ tự động chọn người đầu tiên nếu là tạo mới (chưa có ID ca trực)
    if (!initialData.id && (!staffAccountId || !availableStaff.some(s => s.id === staffAccountId))) {
      staffAccountId = availableStaff[0] ? availableStaff[0].id : '';
    }

    return {
      id: initialData.id || null,
      workDate: initialData.workDate || defaultDate,
      shift: initialData.shift || 'MORNING',
      staffAccountId,
      staffName: initialData.staffName || '',
      staffRole: initialData.staffRole || '',
      roomId,
      roomName: initialData.roomName || roomObj?.name || roomObj?.roomName || '',
      roomCode: initialData.roomCode || roomObj?.code || roomObj?.roomCode || '',
      notes: initialData.notes || '',
      assignedByName: initialData.assignedByName || '',
    };
  }

  // Phân công ca trực mới mặc định
  const defaultStaff = staffList.find(s => s.roomId) || staffList[0];
  return {
    id: null,
    workDate: defaultDate,
    shift: 'MORNING',
    staffAccountId: defaultStaff?.id || '',
    staffName: '',
    staffRole: '',
    roomId: defaultStaff?.roomId || '',
    roomName: '',
    roomCode: '',
    notes: '',
    assignedByName: '',
  };
}

export function DutyScheduleModal(props) {
  if (!props.isOpen) return null;
  return (
    <DutyScheduleModalContent
      key={`${props.initialData?.id || 'new'}-${props.weekStartDate || ''}-${props.initialData?.shift || ''}-${props.initialData?.roomId || ''}`}
      {...props}
    />
  );
}

function DutyScheduleModalContent({
  onClose,
  onSave,
  onDelete,
  readOnly = false,
  initialData = null,
  weekStartDate,
  staffList = [],
  roomList = [],
  existingSchedules = [],
}) {
  const [formData, setFormData] = useState(() =>
    buildInitialFormData({ initialData, weekStartDate, staffList, roomList, existingSchedules })
  );

  const isPast = Boolean(formData.workDate && isShiftPast(formData.workDate, formData.shift));

  const [errors, setErrors] = useState({});

  // Thông tin nhân viên và phòng hiện tại được chọn
  const selectedStaff = useMemo(
    () => staffList.find(s => s.id === formData.staffAccountId),
    [staffList, formData.staffAccountId]
  );

  const selectedRoom = useMemo(
    () => roomList.find(r => r.id === formData.roomId),
    [roomList, formData.roomId]
  );

  // Danh sách nhân viên của phòng (1 bác sĩ chỉ liên kết với 1 phòng)
  const { availableStaff, isPrimary, roleName, isParaclinical } = useMemo(
    () =>
      categorizeStaff({
        roomId: formData.roomId,
        roomObj: selectedRoom,
        staffList,
        existingSchedules,
      }),
    [formData.roomId, selectedRoom, staffList, existingSchedules]
  );

  // Xử lý khi chọn Nhân viên phụ trách ca
  const handleStaffChange = (newStaffId) => {
    setFormData(prev => ({
      ...prev,
      staffAccountId: newStaffId,
    }));
    setErrors(prev => ({
      ...prev,
      staffAccountId: undefined,
    }));
  };

  // Kiểm tra trùng ca trực trong cùng buổi (Conflict check)
  const roomConflict = useMemo(() => {
    if (!formData.workDate || !formData.shift || !formData.roomId || !existingSchedules) return null;
    return existingSchedules.find(
      s => s.workDate === formData.workDate &&
           s.shift === formData.shift &&
           s.roomId === formData.roomId &&
           s.id !== formData.id
    );
  }, [formData.workDate, formData.shift, formData.roomId, formData.id, existingSchedules]);

  const staffConflict = useMemo(() => {
    if (!formData.workDate || !formData.shift || !formData.staffAccountId || !existingSchedules) return null;
    return existingSchedules.find(
      s => s.workDate === formData.workDate &&
           s.shift === formData.shift &&
           s.staffAccountId === formData.staffAccountId &&
           s.id !== formData.id
    );
  }, [formData.workDate, formData.shift, formData.staffAccountId, formData.id, existingSchedules]);

  const validate = () => {
    const errs = {};
    if (isPast) {
      errs.general = 'Không thể phân công hoặc chỉnh sửa lịch trực trong quá khứ';
    }
    if (!formData.staffAccountId) errs.staffAccountId = `Vui lòng chọn ${roleName || 'nhân viên'} trực`;
    if (!formData.roomId) errs.roomId = 'Vui lòng chọn phòng làm việc';

    // Ràng buộc nghiêm ngặt nghiệp vụ y tế: Phòng khám lâm sàng bắt buộc là Bác sĩ, Cận lâm sàng là KTV
    if (selectedStaff && isParaclinical && selectedStaff.role !== 'TECHNICIAN') {
      errs.staffAccountId = 'Phòng cận lâm sàng chỉ có thể phân công Kỹ thuật viên';
    }
    if (selectedStaff && !isParaclinical && selectedStaff.role !== 'DOCTOR') {
      errs.staffAccountId = 'Phòng khám lâm sàng bắt buộc phải phân công Bác sĩ';
    }

    if (roomConflict) {
      errs.roomConflict = `Phòng này đã có ${roomConflict.staffName || 'người khác'} trực trong ca này`;
    }
    if (staffConflict) {
      errs.staffConflict = `Nhân viên này đã được phân công ca trực này trong ngày`;
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (readOnly) return;
    if (!validate()) return;

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

  // Xác định tên hiển thị của nhân viên (kể cả khi staffList trống ở role khác)
  const displayStaffName = selectedStaff?.fullName || selectedStaff?.name || formData.staffName;
  const displayStaffRole =
    selectedStaff?.role === 'DOCTOR' || formData.staffRole === 'DOCTOR'
      ? 'Bác sĩ'
      : selectedStaff?.role === 'TECHNICIAN' || formData.staffRole === 'TECHNICIAN'
        ? 'Kỹ thuật viên'
        : roleName || 'Nhân viên';

  const displayRoomTitle =
    (selectedRoom?.code || formData.roomCode ? `[${selectedRoom?.code || formData.roomCode}] ` : '') +
    (selectedRoom?.name || selectedRoom?.roomName || formData.roomName || 'Phòng làm việc');

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
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-bold text-slate-800">
                {readOnly
                  ? (formData.id ? 'Chi tiết ca trực' : 'Chi tiết ca trực (Chưa phân công)')
                  : (formData.id ? 'Chỉnh sửa ca trực' : 'Phân công ca trực mới')}
              </h3>
              {readOnly && (
                <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-200/80 text-slate-700 flex items-center gap-1">
                  <Eye className="w-3 h-3 text-slate-600" />
                  Chế độ chỉ xem
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              {readOnly
                ? 'Thông tin phân công lịch trực phòng khám'
                : 'Nhân viên phụ trách ca và phòng làm việc được liên kết tự động theo phân công'}
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

        {/* View-Only Mode for Non-Admin Roles */}
        {readOnly ? (
          <div className="p-6 space-y-4">
            {/* Ca trực & Ngày trực */}
            <div className={`p-4 rounded-2xl border flex items-center justify-between ${
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
            </div>

            {/* Nhân viên phụ trách */}
            <div>
              <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                Nhân viên phụ trách ca
              </label>
              {displayStaffName ? (
                <div className="p-3.5 rounded-xl bg-teal-50/60 border border-teal-200/80 flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-teal-100 text-teal-700 flex items-center justify-center font-bold text-sm shrink-0">
                    <User className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-teal-950">
                      {displayStaffName}
                    </div>
                    <div className="text-xs font-medium text-teal-700 mt-0.5">
                      {displayStaffRole === 'Bác sĩ'
                        ? 'Bác sĩ phụ trách phòng khám'
                        : displayStaffRole === 'Kỹ thuật viên'
                          ? 'Kỹ thuật viên phụ trách cận lâm sàng'
                          : 'Nhân viên phụ trách ca trực'}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-500 italic flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>Ca trực này hiện chưa có nhân viên được phân công</span>
                </div>
              )}
            </div>

            {/* Phòng làm việc */}
            <div>
              <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                Phòng làm việc
              </label>
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex items-center gap-2.5 text-sm font-semibold text-slate-800">
                <DoorOpen className="w-4 h-4 text-teal-600 shrink-0" />
                <span>
                  {displayRoomTitle}
                  {(selectedRoom?.departmentName || formData.departmentName) && (
                    <span className="text-xs font-normal text-slate-500 ml-1.5">
                      ({selectedRoom?.departmentName || formData.departmentName})
                    </span>
                  )}
                </span>
              </div>
            </div>

            {/* Ghi chú */}
            <div>
              <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                Ghi chú ca trực
              </label>
              <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-700 min-h-[42px] flex items-center">
                {formData.notes ? formData.notes : <span className="text-slate-400 italic">Không có ghi chú</span>}
              </div>
            </div>

            {/* Người phân công (Audit info) */}
            {formData.assignedByName && (
              <div className="text-[11px] text-slate-500 flex items-center justify-between pt-1 border-t border-slate-100">
                <span>Người phân công: <strong className="text-slate-700">{formData.assignedByName}</strong></span>
              </div>
            )}

            {/* Footer readOnly */}
            <div className="pt-3 flex items-center justify-end border-t border-slate-100">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                className="px-5 text-xs font-semibold cursor-pointer"
              >
                Đóng
              </Button>
            </div>
          </div>
        ) : (
          /* Form Body khi Admin sửa/phân công */
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

            {/* NHÂN VIÊN PHỤ TRÁCH CA (Bác sĩ cho phòng khám, KTV cho phòng cận lâm sàng) */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  {roleName ? `${roleName.toUpperCase()} PHỤ TRÁCH CA` : 'NHÂN VIÊN PHỤ TRÁCH CA'} <span className="text-rose-500">*</span>
                </label>
                {availableStaff.length > 0 && (
                  <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full border ${
                    isPrimary
                      ? 'text-teal-700 bg-teal-50 border-teal-200'
                      : 'text-sky-700 bg-sky-50 border-sky-200'
                  }`}>
                    {isPrimary
                      ? `Phòng có ${availableStaff.length} ${roleName} phụ trách`
                      : `${availableStaff.length} ${roleName} phù hợp`}
                  </span>
                )}
              </div>
              <div className="relative">
                <User className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                <select
                  value={formData.staffAccountId}
                  onChange={e => handleStaffChange(e.target.value)}
                  className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm text-slate-800 bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all cursor-pointer ${
                    errors.staffAccountId || errors.staffConflict ? 'border-rose-400 bg-rose-50/20' : 'border-slate-200'
                  }`}
                >
                  <option value="">
                    {isPrimary
                      ? `-- Chọn ${roleName} thuộc phòng này (${availableStaff.length} người) --`
                      : `-- Chọn ${roleName} phụ trách ca (${availableStaff.length} người) --`}
                  </option>
                  {availableStaff.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.fullName || s.name} ({s.role === 'DOCTOR' ? 'Bác sĩ' : 'Kỹ thuật viên'})
                      {!isPrimary && s.departmentName ? ` - ${s.departmentName}` : ''}
                    </option>
                  ))}
                </select>
              </div>
              {errors.staffAccountId && (
                <p className="text-xs text-rose-500 mt-1">{errors.staffAccountId}</p>
              )}

              {/* Cảnh báo khi phòng chưa có nhân viên liên kết */}
              {availableStaff.length === 0 && (
                <div className="mt-2 p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-start gap-2.5 animate-in fade-in">
                  <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold">Chưa có {roleName} liên kết với phòng này:</span>{' '}
                    Phòng khám này hiện chưa có {roleName} nào được phân công làm việc trong hệ thống. Vui lòng vào mục <strong>Quản lý Tài khoản</strong> để gán {roleName} vào phòng này trước khi phân công ca trực.
                  </div>
                </div>
              )}

              {/* Thông tin phản hồi về nhân viên được chọn */}
              {selectedStaff && (
                <div className="mt-1.5 flex items-center gap-1.5 text-xs text-teal-800 bg-teal-50/90 px-3 py-1.5 rounded-xl border border-teal-200/80 font-medium">
                  <CheckCircle2 className="w-3.5 h-3.5 text-teal-600 shrink-0" />
                  <span>
                    {isPrimary ? `${roleName} trực thuộc phòng:` : `${roleName} phụ trách ca:`}{' '}
                    <strong className="font-semibold text-teal-950">
                      {selectedStaff.fullName || selectedStaff.name} ({selectedStaff.role === 'DOCTOR' ? 'Bác sĩ' : 'Kỹ thuật viên'})
                    </strong>
                  </span>
                </div>
              )}
            </div>

            {/* PHÒNG LÀM VIỆC (Hiển thị tên phòng cố định, không còn dropdown) */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Phòng làm việc <span className="text-rose-500">*</span>
                </label>
                <span className="text-[11px] font-semibold text-teal-700 bg-teal-50 px-2.5 py-0.5 rounded-full border border-teal-200/80 flex items-center gap-1">
                  <Link2 className="w-3 h-3 text-teal-600" />
                  Đã cố định theo ô chọn
                </span>
              </div>
              <div className="w-full px-4 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-sm font-semibold text-slate-800 flex items-center justify-between shadow-2xs">
                <div className="flex items-center gap-2.5 truncate">
                  <DoorOpen className="w-4 h-4 text-teal-600 shrink-0" />
                  <span className="truncate">
                    {selectedRoom ? (
                      <>
                        {selectedRoom.code ? <span className="text-teal-700 font-bold">[{selectedRoom.code}] </span> : ''}
                        <span>{selectedRoom.name || selectedRoom.roomName}</span>
                        {selectedRoom.departmentName ? (
                          <span className="text-xs font-normal text-slate-500 ml-1.5">
                            ({selectedRoom.departmentName})
                          </span>
                        ) : null}
                      </>
                    ) : (
                      formData.roomName ? `[${formData.roomCode || ''}] ${formData.roomName}` : 'Phòng làm việc chỉ định'
                    )}
                  </span>
                </div>
                <span className="text-[11px] font-bold text-slate-500 bg-white px-2 py-0.5 rounded-md border border-slate-200/80 shrink-0">
                  Cố định
                </span>
              </div>
              {errors.roomId && (
                <p className="text-xs text-rose-500 mt-1">{errors.roomId}</p>
              )}
            </div>

            {/* CẢNH BÁO XUNG ĐỘT (Conflict Warning) */}
            {roomConflict && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-900 flex items-start gap-2 animate-in fade-in">
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold">Trùng phòng trực:</span>{' '}
                  Phòng <strong>{selectedRoom?.name || selectedRoom?.roomName || 'này'}</strong> đã có <strong>{roomConflict.staffName || 'nhân viên khác'}</strong> trực trong {formData.shift === 'MORNING' ? 'Ca Sáng' : 'Ca Chiều'}. Mỗi phòng chỉ có 1 nhân viên trực trong mỗi ca.
                </div>
              </div>
            )}

            {staffConflict && (
              <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-start gap-2 animate-in fade-in">
                <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold">Trùng lịch nhân viên:</span>{' '}
                  Nhân viên <strong>{selectedStaff?.fullName || 'này'}</strong> đã có lịch trực trong {formData.shift === 'MORNING' ? 'Ca Sáng' : 'Ca Chiều'} ngày {formatDisplayDate(formData.workDate)}.
                </div>
              </div>
            )}

            {/* Ghi chú thêm */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                Ghi chú ca trực
              </label>
              <input
                type="text"
                disabled={isPast}
                value={formData.notes || ''}
                onChange={e => setFormData(prev => ({ ...prev, notes: e.target.value }))}
                placeholder="Ví dụ: Trực chính, khám cấp cứu..."
                maxLength={255}
                className={`w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-800 bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all placeholder:text-slate-400 ${
                  isPast ? 'bg-slate-50 cursor-not-allowed opacity-80' : ''
                }`}
              />
            </div>

            {/* Warning banner if in the past */}
            {isPast && (
              <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-center gap-2 animate-in fade-in">
                <span className="font-semibold">Lưu ý:</span>
                <span>Ca trực này đã kết thúc hoặc thuộc thời gian trong quá khứ nên chỉ có thể xem thông tin, không thể chỉnh sửa hay phân công lại.</span>
              </div>
            )}

            {/* Actions for Admin */}
            <div className="pt-3 flex items-center justify-between gap-3 border-t border-slate-100">
              <div>
                {formData.id && !isPast && onDelete && (
                  <button
                    type="button"
                    onClick={() => {
                      onClose();
                      onDelete({
                        ...formData,
                        id: formData.id,
                        staffName: selectedStaff?.fullName || selectedStaff?.name || formData.staffName || 'nhân viên này',
                      });
                    }}
                    className="px-3.5 py-2 rounded-xl text-xs font-semibold text-rose-600 bg-rose-50 border border-rose-200 hover:bg-rose-100 hover:border-rose-300 transition-colors flex items-center gap-1.5 cursor-pointer shadow-2xs"
                    title="Gỡ ca trực đã phân công"
                  >
                    <Trash2 className="w-3.5 h-3.5 text-rose-600" />
                    <span>Gỡ ca trực</span>
                  </button>
                )}
              </div>

              <div className="flex items-center gap-2.5">
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
                    disabled={Boolean(roomConflict || staffConflict || availableStaff.length === 0)}
                    className="px-5 text-xs font-semibold shadow-sm shadow-teal-500/20 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {formData.id ? 'Cập nhật phân công' : 'Lưu phân công'}
                  </Button>
                )}
              </div>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

export default DutyScheduleModal;
