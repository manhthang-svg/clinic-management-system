import { useEffect } from 'react';
import {
  X,
  User,
  ShieldCheck,
  Building2,
  Stethoscope,
  Microscope,
  AlertTriangle,
  CheckCircle2,
  MapPin,
  ShieldAlert,
  Play,
  SkipForward,
  Ban,
} from 'lucide-react';

const FALLBACK_ORDER = {
  id: '',
  queueNumber: 0,
  patientName: '',
  gender: '',
  age: 0,
  patientId: '',
  orderCode: '',
  priority: 'NORMAL',
  priorityLabel: 'Thông thường',
  insuranceType: 'FEE',
  phone: '',
  cccd: '',
  insuranceNumber: '',
  address: '',
  chiefComplaint: '',
  clinicalDiagnosis: '',
  icd10: '',
  physicalExam: '',
  doctorName: '',
  doctorRoom: '',
  hasAllergyAlert: false,
  allergies: 'Không ghi nhận tiền sử dị ứng',
  medicalHistory: 'Chưa ghi nhận bệnh lý đặc biệt',
  allOrdersToday: [],
  status: 'WAITING',
};

export function PatientClinicalDrawer({
  isOpen,
  onClose,
  order,
  onStartOrder,
  onSkipOrder,
  onCompleteOrder,
  onOpenRejectModal,
}) {
  const currentOrder = order || FALLBACK_ORDER;

  // Xử lý phím Escape để đóng drawer
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const isCurrentRoomWaiting = currentOrder.status === 'WAITING';
  const isCurrentRoomSkipped = currentOrder.status === 'SKIPPED';
  const isCurrentRoomInProgress = currentOrder.status === 'IN_PROGRESS';
  const isCurrentRoomCompleted = currentOrder.status === 'COMPLETED';
  const isCancelled = currentOrder.status === 'CANCELLED';

  return (
    <div
      className={`fixed inset-0 z-50 overflow-hidden transition-all duration-500 ease-in-out ${
        isOpen ? 'visible pointer-events-auto' : 'invisible pointer-events-none delay-500'
      }`}
      aria-hidden={!isOpen}
    >
      {/* Lớp nền mờ (Backdrop) với hiệu ứng fade mượt mà, không gây giật màn hình chính */}
      <div
        className={`fixed inset-0 bg-slate-900/40 transition-opacity duration-500 ease-in-out ${
          isOpen ? 'opacity-100' : 'opacity-0'
        }`}
        onClick={onClose}
      />

      {/* Khung trượt từ phải qua trái và đóng từ trái qua phải - Tăng kích thước rộng rãi hơn để dễ nhìn */}
      <div className="fixed inset-y-0 right-0 max-w-full flex pl-6 sm:pl-10 pointer-events-none">
        <div
          className={`w-screen max-w-2xl lg:max-w-[680px] xl:max-w-[720px] bg-white shadow-2xl border-l border-slate-200/90 flex flex-col pointer-events-auto transform transition-transform duration-500 ease-in-out will-change-transform ${
            isOpen ? 'translate-x-0' : 'translate-x-full'
          }`}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Drawer Header - Rõ ràng, dễ nhìn hơn */}
          <div className="px-6 py-4 sm:py-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/90 shrink-0">
            <div className="flex items-center gap-3.5">
              <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-xl bg-[#0d9488]/10 text-[#0d9488] flex items-center justify-center font-black text-base sm:text-lg border border-[#0d9488]/20 shrink-0">
                #{currentOrder.queueNumber < 10 ? `0${currentOrder.queueNumber}` : currentOrder.queueNumber}
              </div>
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-lg sm:text-xl font-extrabold text-slate-900 tracking-tight">
                    {currentOrder.patientName}
                  </h3>
                  <span className="text-sm font-semibold text-slate-500">
                    ({currentOrder.gender}, {currentOrder.age} tuổi)
                  </span>
                </div>
                <div className="flex items-center gap-2 text-xs sm:text-sm text-slate-500 mt-0.5">
                  <span className="font-mono font-bold text-slate-700 bg-slate-200/70 px-1.5 py-0.5 rounded text-xs">{currentOrder.patientId}</span>
                  <span>•</span>
                  <span>Phiếu: <strong className="text-slate-800 font-mono font-bold">{currentOrder.orderCode}</strong></span>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2.5">
              <span
                className={`px-3 py-1 rounded-full text-xs sm:text-sm font-bold border shadow-2xs ${
                  currentOrder.priority === 'PRIORITY'
                    ? 'bg-amber-50 text-amber-700 border-amber-200'
                    : 'bg-teal-50 text-teal-700 border-teal-200'
                }`}
              >
                {currentOrder.priorityLabel}
              </span>
              <button
                type="button"
                onClick={onClose}
                className="w-9 h-9 rounded-xl hover:bg-slate-200 text-slate-400 hover:text-slate-700 flex items-center justify-center transition-colors cursor-pointer"
                title="Đóng (Esc)"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* Drawer Scrollable Content - Tăng cỡ chữ và khoảng cách rộng rãi hơn */}
          <div className="flex-1 overflow-y-auto p-6 sm:p-7 space-y-6 text-sm text-slate-700">
            {/* 1. KHỐI THÔNG TIN HÀNH CHÍNH */}
            <div className="p-5 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <div className="flex items-center gap-2.5">
                  <User className="w-5 h-5 text-[#0d9488]" />
                  <h4 className="font-extrabold text-slate-900 text-xs sm:text-sm uppercase tracking-wider">
                    Thông tin hành chính bệnh nhân
                  </h4>
                </div>
                {currentOrder.insuranceType === 'BHYT' ? (
                  <span className="px-3 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1.5 shadow-2xs">
                    <ShieldCheck className="w-4 h-4" />
                    BHYT
                  </span>
                ) : (
                  <span className="px-3 py-1 rounded-full text-xs font-bold bg-slate-100 text-slate-700 border border-slate-200 shadow-2xs">
                    Viện phí
                  </span>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-slate-600">
                <div>
                  <span className="text-slate-400 block text-xs font-medium">Họ và tên:</span>
                  <span className="font-black text-slate-900 text-base">{currentOrder.patientName}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-xs font-medium">Mã hồ sơ bệnh nhân:</span>
                  <span className="font-mono font-bold text-teal-800 text-sm">{currentOrder.patientId}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-xs font-medium">Năm sinh / Giới tính:</span>
                  <span className="font-bold text-slate-800 text-sm">{currentOrder.age} tuổi • {currentOrder.gender}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-xs font-medium">Số điện thoại liên hệ:</span>
                  <span className="font-bold text-slate-800 text-sm">{currentOrder.phone}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-xs font-medium">Số CCCD / Định danh:</span>
                  <span className="font-mono font-semibold text-slate-800 text-sm">{currentOrder.cccd}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-xs font-medium">Số thẻ BHYT:</span>
                  {currentOrder.insuranceNumber ? (
                    <span className="font-mono font-bold text-emerald-800 text-sm">{currentOrder.insuranceNumber}</span>
                  ) : (
                    <span className="text-slate-400 italic text-sm">Không áp dụng</span>
                  )}
                </div>
                <div className="sm:col-span-2">
                  <span className="text-slate-400 block text-xs font-medium">Địa chỉ cư trú:</span>
                  <span className="text-slate-800 flex items-center gap-1.5 mt-0.5 text-sm font-medium">
                    <MapPin className="w-4 h-4 text-slate-400 shrink-0" />
                    <span>{currentOrder.address}</span>
                  </span>
                </div>
              </div>
            </div>

            {/* 2. KHỐI THÔNG TIN LÂM SÀNG (TỪ BÁC SĨ KHÁM CHUYỂN SANG) */}
            <div className="p-5 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-4">
              <div className="flex items-center gap-2.5 border-b border-slate-100 pb-3">
                <Stethoscope className="w-5 h-5 text-[#0d9488]" />
                <h4 className="font-extrabold text-slate-900 text-xs sm:text-sm uppercase tracking-wider">
                  Thông tin lâm sàng từ Bác sĩ chỉ định
                </h4>
              </div>

              {/* Lý do khám (Chief Complaint) */}
              <div className="space-y-1.5">
                <span className="text-slate-600 font-bold block text-xs">
                  Lý do khám (Chief Complaint):
                </span>
                <div className="p-3.5 rounded-xl bg-amber-50/80 border border-amber-200/80 text-slate-900 font-bold text-sm leading-relaxed">
                  💬 {currentOrder.chiefComplaint}
                </div>
              </div>

              {/* Chẩn đoán sơ bộ kèm mã ICD-10 */}
              <div className="space-y-1.5">
                <span className="text-slate-600 font-bold block text-xs">
                  Chẩn đoán sơ bộ & Mã ICD-10:
                </span>
                <div className="p-3.5 rounded-xl bg-teal-50/70 border border-teal-200/80 text-teal-950 font-bold text-sm flex items-center justify-between gap-3">
                  <span className="text-slate-900 font-extrabold">{currentOrder.clinicalDiagnosis}</span>
                  <span className="px-2.5 py-1 bg-teal-600 text-white rounded-lg text-xs font-mono font-bold tracking-wider shrink-0 shadow-2xs">
                    {currentOrder.icd10}
                  </span>
                </div>
              </div>

              {/* Khám lâm sàng & Triệu chứng */}
              <div className="space-y-1.5">
                <span className="text-slate-600 font-bold block text-xs">
                  Mô tả khám lâm sàng & Triệu chứng thực thể (Định vị vùng cần khảo sát):
                </span>
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 text-slate-700 leading-relaxed font-medium text-sm">
                  {currentOrder.physicalExam}
                </div>
              </div>

              {/* Bác sĩ và phòng chỉ định */}
              <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-100 text-xs sm:text-sm">
                <div>
                  <span className="text-slate-400 block text-xs">Bác sĩ chỉ định:</span>
                  <p className="font-bold text-slate-900 text-sm mt-0.5">{currentOrder.doctorName}</p>
                </div>
                <div>
                  <span className="text-slate-400 block text-xs">Nơi gửi y lệnh:</span>
                  <p className="font-bold text-slate-900 text-sm mt-0.5">{currentOrder.doctorRoom}</p>
                </div>
              </div>
            </div>

            {/* 3. KHỐI TIỀN SỬ BỆNH & CẢNH BÁO AN TOÀN (MEDICAL HISTORY & ALLERGIES) */}
            <div className="p-5 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-4">
              <div className="flex items-center gap-2.5 border-b border-slate-100 pb-3">
                <ShieldAlert className="w-5 h-5 text-rose-600" />
                <h4 className="font-extrabold text-slate-900 text-xs sm:text-sm uppercase tracking-wider">
                  Tiền sử bệnh & Cảnh báo an toàn
                </h4>
              </div>

              {/* Cảnh báo dị ứng nổi bật (Allergies) */}
              <div className="space-y-1.5">
                <span className="text-slate-700 font-bold block text-xs">
                  Cảnh báo dị ứng (Allergies):
                </span>
                <div
                  className={`p-4 rounded-xl border flex items-start gap-3 ${
                    currentOrder.hasAllergyAlert
                      ? 'bg-rose-50 border-rose-200/90 text-rose-950'
                      : 'bg-emerald-50/70 border-emerald-200 text-emerald-950'
                  }`}
                >
                  {currentOrder.hasAllergyAlert ? (
                    <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5 animate-pulse" />
                  ) : (
                    <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                  )}
                  <div className="text-sm font-bold leading-relaxed">
                    {currentOrder.allergies}
                  </div>
                </div>
              </div>

              {/* Tiền sử bệnh nền */}
              <div className="space-y-1.5 pt-1">
                <span className="text-slate-600 font-bold block text-xs">
                  Tiền sử bệnh nền & phẫu thuật:
                </span>
                <p className="p-3 bg-slate-50 rounded-xl border border-slate-200/80 text-slate-800 font-medium text-sm leading-relaxed">
                  {currentOrder.medicalHistory}
                </p>
              </div>
            </div>

            {/* 4. DANH SÁCH TOÀN BỘ CHỈ ĐỊNH TRONG NGÀY CỦA BỆNH NHÂN */}
            <div className="p-5 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <div className="flex items-center gap-2.5">
                  <Microscope className="w-5 h-5 text-[#0d9488]" />
                  <h4 className="font-extrabold text-slate-900 text-xs sm:text-sm uppercase tracking-wider">
                    Danh sách chỉ định cận lâm sàng trong ngày
                  </h4>
                </div>
                <span className="text-xs font-bold text-slate-500 bg-slate-100 px-2.5 py-0.5 rounded-full">
                  {currentOrder.allOrdersToday?.length || 1} dịch vụ
                </span>
              </div>

              <div className="space-y-2.5">
                {currentOrder.allOrdersToday?.map((subOrder) => (
                  <div
                    key={subOrder.id}
                    className={`p-3.5 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                      subOrder.isCurrentRoom
                        ? 'bg-[#dcefec]/50 border-[#0d9488]/40 ring-1 ring-[#0d9488]/30 shadow-2xs'
                        : 'bg-slate-50 border-slate-200/80'
                    }`}
                  >
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-extrabold text-slate-900 text-sm">
                          {subOrder.serviceName}
                        </span>
                        {subOrder.isCurrentRoom && (
                          <span className="px-2.5 py-0.5 bg-[#0d9488] text-white rounded-md text-xs font-bold shadow-2xs">
                            Tại phòng này
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-slate-500 flex items-center gap-1.5 font-medium">
                        <Building2 className="w-4 h-4 text-slate-400 shrink-0" />
                        <span>{subOrder.roomName}</span>
                      </div>
                    </div>

                    <span
                      className={`px-3 py-1 rounded-full text-xs font-bold whitespace-nowrap border self-start sm:self-auto shadow-2xs ${
                        subOrder.status === 'COMPLETED'
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : subOrder.status === 'IN_PROGRESS'
                          ? 'bg-purple-50 text-purple-700 border-purple-200'
                          : subOrder.status === 'CANCELLED'
                          ? 'bg-rose-50 text-rose-700 border-rose-200'
                          : 'bg-sky-50 text-sky-700 border-sky-200'
                      }`}
                    >
                      {subOrder.status === 'COMPLETED' && 'Đã có kết quả'}
                      {subOrder.status === 'IN_PROGRESS' && 'Đang thực hiện'}
                      {subOrder.status === 'WAITING' && 'Chờ tiếp nhận'}
                      {subOrder.status === 'CANCELLED' && 'Đã từ chối'}
                    </span>
                  </div>
                ))}
              </div>
              <p className="text-xs text-slate-400 italic">
                * Kỹ thuật viên có thể xem lịch trình các phòng để hướng dẫn bệnh nhân di chuyển sau khi thực hiện xong kỹ thuật này.
              </p>
            </div>
          </div>

          {/* 5. CỤM NÚT ĐIỀU HƯỚNG HÀNH ĐỘNG (ACTION FOOTER) - Lớn hơn, dễ bấm hơn */}
          <div className="p-4 sm:p-5 px-6 border-t border-slate-200 bg-slate-50/95 flex flex-wrap items-center justify-between gap-3 shrink-0">
            <button
              type="button"
              disabled={isCancelled || isCurrentRoomCompleted}
              onClick={() => onOpenRejectModal(currentOrder)}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-bold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-xl transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed shadow-2xs"
            >
              <Ban className="w-4 h-4" />
              <span>Hủy / Từ chối (Lý do)</span>
            </button>

            <div className="flex items-center gap-3">
              {isCurrentRoomWaiting && (
                <>
                  <button
                    type="button"
                    onClick={() => onStartOrder(currentOrder)}
                    className="inline-flex items-center gap-2 px-6 py-2.5 text-sm font-extrabold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer active:scale-95"
                  >
                    <Play className="w-4 h-4 fill-current" />
                    <span>Tiếp nhận</span>
                  </button>
                  {onSkipOrder && (
                    <button
                      type="button"
                      onClick={() => onSkipOrder(currentOrder)}
                      className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-xl transition-all cursor-pointer shadow-2xs active:scale-95"
                    >
                      <SkipForward className="w-4 h-4 text-slate-600" />
                      <span>Bỏ qua</span>
                    </button>
                  )}
                </>
              )}

              {isCurrentRoomSkipped && (
                <div className="flex items-center gap-2">
                  <span className="px-4 py-2.5 text-sm font-bold text-amber-800 bg-amber-50 border border-amber-200 rounded-xl flex items-center gap-2 shadow-2xs">
                    <SkipForward className="w-4 h-4 text-amber-600" />
                    Đã bỏ qua
                  </span>
                  <button
                    type="button"
                    onClick={() => onStartOrder(currentOrder)}
                    className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-extrabold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer active:scale-95"
                  >
                    <Play className="w-4 h-4 fill-current" />
                    <span>Tiếp nhận lại</span>
                  </button>
                </div>
              )}

              {isCancelled && (
                <span className="px-5 py-2.5 text-sm font-bold text-rose-800 bg-rose-50 border border-rose-200 rounded-xl flex items-center gap-2 shadow-2xs">
                  <Ban className="w-4 h-4 text-rose-600" />
                  Đã từ chối y lệnh
                </span>
              )}

              <button
                type="button"
                onClick={onClose}
                className="px-5 py-2.5 text-sm font-bold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 rounded-xl transition-all cursor-pointer shadow-2xs"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default PatientClinicalDrawer;
