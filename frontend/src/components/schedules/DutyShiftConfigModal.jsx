import { useState, useRef, useEffect } from 'react';
import { X, Clock, Sun, Sunset, RotateCcw, ShieldAlert, Check } from 'lucide-react';
import { Button } from '../ui/button';

const MORNING_HOURS = ['00', '01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11'];
const AFTERNOON_HOURS = ['12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23'];
const MINUTES = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0'));

/**
 * Cột con lăn (WheelColumn) hiển thị đúng 3 giá trị theo chiều dọc:
 * - Trên: 1 giá trị trước đó (prevVal) - mờ hơn, click để lùi
 * - Giữa: 1 giá trị hiện tại (currVal) - giá trị đang chọn, nổi bật trên thanh highlight
 * - Dưới: 1 giá trị tiếp theo (nextVal) - mờ hơn, click để tiến
 * Hỗ trợ lăn chuột (mouse wheel), vuốt cảm ứng và phím mũi tên Lên/Xuống.
 */
function WheelColumn({ items, value, onChange, theme = 'amber' }) {
  const len = items.length;
  const currentIndex = items.indexOf(value) !== -1 ? items.indexOf(value) : 0;
  const prevIndex = (currentIndex - 1 + len) % len;
  const nextIndex = (currentIndex + 1) % len;

  const prevVal = items[prevIndex];
  const currVal = items[currentIndex];
  const nextVal = items[nextIndex];

  const columnRef = useRef(null);
  const lastWheelRef = useRef(0);
  const touchStartYRef = useRef(null);

  // Lắng nghe sự kiện lăn chuột (wheel) non-passive để ngăn cuộn trang modal
  useEffect(() => {
    const el = columnRef.current;
    if (!el) return;

    const handleWheel = (e) => {
      e.preventDefault();
      e.stopPropagation();

      const now = Date.now();
      // Throttle 75ms để con lăn mượt mà, không bị nhảy loạn xạ
      if (now - lastWheelRef.current < 75) {
        return;
      }
      lastWheelRef.current = now;

      // Giữ Shift để cuộn nhanh 5 đơn vị
      const step = e.shiftKey ? 5 : 1;
      if (e.deltaY < 0) {
        // Cuộn lên -> lùi giá trị
        onChange(items[(currentIndex - step + len) % len]);
      } else if (e.deltaY > 0) {
        // Cuộn xuống -> tiến giá trị
        onChange(items[(currentIndex + step) % len]);
      }
    };

    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => {
      el.removeEventListener('wheel', handleWheel);
    };
  }, [items, currentIndex, len, onChange]);

  const handleTouchStart = (e) => {
    touchStartYRef.current = e.touches[0].clientY;
  };

  const handleTouchMove = (e) => {
    if (touchStartYRef.current === null) return;
    const diffY = e.touches[0].clientY - touchStartYRef.current;
    if (Math.abs(diffY) > 20) {
      if (diffY > 0) {
        // Vuốt xuống -> lùi giá trị
        onChange(items[(currentIndex - 1 + len) % len]);
      } else {
        // Vuốt lên -> tiến giá trị
        onChange(items[(currentIndex + 1) % len]);
      }
      touchStartYRef.current = e.touches[0].clientY;
    }
  };

  const handleTouchEnd = () => {
    touchStartYRef.current = null;
  };

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      onChange(items[(currentIndex - 1 + len) % len]);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      onChange(items[(currentIndex + 1) % len]);
    }
  };

  const textColor = theme === 'amber' ? 'text-amber-950' : 'text-teal-950';

  return (
    <div
      ref={columnRef}
      tabIndex={0}
      onKeyDown={handleKeyDown}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      className="flex flex-col items-center w-16 sm:w-20 select-none z-10 focus:outline-none focus-visible:ring-1 focus-visible:ring-slate-300 rounded-lg cursor-ns-resize"
      title="Cuộn chuột hoặc nhấp để đổi giá trị (Shift để nhảy nhanh 5)"
    >
      {/* 1 giá trị ở TRÊN */}
      <button
        type="button"
        onClick={() => onChange(prevVal)}
        className="h-7 w-full flex items-center justify-center text-xs sm:text-sm font-medium text-slate-400 hover:text-slate-800 hover:bg-slate-100/80 rounded-md transition-all scale-90 opacity-60 hover:opacity-100 cursor-pointer"
        title={`Chọn ${prevVal}`}
      >
        {prevVal}
      </button>

      {/* 1 giá trị ở GIỮA (nổi bật, đang chọn) */}
      <div className={`h-10 w-full flex items-center justify-center text-lg sm:text-xl font-black tracking-wider ${textColor}`}>
        {currVal}
      </div>

      {/* 1 giá trị ở DƯỚI */}
      <button
        type="button"
        onClick={() => onChange(nextVal)}
        className="h-7 w-full flex items-center justify-center text-xs sm:text-sm font-medium text-slate-400 hover:text-slate-800 hover:bg-slate-100/80 rounded-md transition-all scale-90 opacity-60 hover:opacity-100 cursor-pointer"
        title={`Chọn ${nextVal}`}
      >
        {nextVal}
      </button>
    </div>
  );
}

/**
 * Bộ chọn thời gian dạng con lăn 3 giá trị (trên, giữa, dưới)
 */
function TimeWheelPicker({
  value,
  onChange,
  hours,
  theme = 'amber',
}) {
  const parts = (value || '').split(':');
  const safeH = hours.includes(parts[0]) ? parts[0] : hours[0];
  const safeM = MINUTES.includes(parts[1]) ? parts[1] : '00';

  const borderColor = theme === 'amber' ? 'border-amber-200' : 'border-teal-200';
  const highlightBg = theme === 'amber'
    ? 'bg-amber-100/75 border-amber-300 text-amber-900'
    : 'bg-teal-100/75 border-teal-300 text-teal-900';
  const colonColor = theme === 'amber' ? 'text-amber-800' : 'text-teal-800';

  return (
    <div className={`relative flex items-center justify-center bg-white rounded-xl border ${borderColor} px-3 py-2 shadow-2xs transition-all overflow-hidden`}>
      {/* Thanh highlight hàng giữa (active row) */}
      <div
        className={`absolute inset-x-3 top-1/2 -translate-y-1/2 h-10 rounded-xl border shadow-2xs pointer-events-none transition-all ${highlightBg}`}
      />

      {/* Gradient mờ cylinder phía trên và dưới tạo chiều sâu con lăn */}
      <div className="pointer-events-none absolute top-0 inset-x-0 h-2 bg-gradient-to-b from-white/90 to-transparent z-20" />
      <div className="pointer-events-none absolute bottom-0 inset-x-0 h-2 bg-gradient-to-t from-white/90 to-transparent z-20" />

      {/* Cột con lăn Giờ (3 giá trị) */}
      <WheelColumn
        items={hours}
        value={safeH}
        onChange={(newH) => onChange(`${newH}:${safeM}`)}
        theme={theme}
      />

      {/* Dấu ':' ở giữa */}
      <div className="flex flex-col items-center justify-center w-6 select-none z-10">
        <div className="h-7" />
        <div className="h-10 flex items-center justify-center">
          <span className={`font-black text-lg sm:text-xl pb-0.5 ${colonColor}`}>:</span>
        </div>
        <div className="h-7" />
      </div>

      {/* Cột con lăn Phút (3 giá trị) */}
      <WheelColumn
        items={MINUTES}
        value={safeM}
        onChange={(newM) => onChange(`${safeH}:${newM}`)}
        theme={theme}
      />
    </div>
  );
}

function DutyShiftConfigModalContent({
  onClose,
  weekStartDate,
  weekEndDate,
  initialConfig,
  onSave,
}) {
  const [morningStart, setMorningStart] = useState(initialConfig?.morningStartTime || '08:00');
  const [morningEnd, setMorningEnd] = useState(initialConfig?.morningEndTime || '11:30');
  const [afternoonStart, setAfternoonStart] = useState(initialConfig?.afternoonStartTime || '13:00');
  const [afternoonEnd, setAfternoonEnd] = useState(initialConfig?.afternoonEndTime || '17:30');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleResetDefault = () => {
    setMorningStart('08:00');
    setMorningEnd('11:30');
    setAfternoonStart('13:00');
    setAfternoonEnd('17:30');
    setError('');
  };

  const validate = () => {
    // Validate Ca sáng: 00:00 - 11:59
    if (!morningStart || !morningEnd) {
      return 'Vui lòng nhập đầy đủ giờ bắt đầu và giờ kết thúc cho Ca Sáng';
    }
    if (morningStart < '00:00' || morningStart > '11:59') {
      return 'Giờ bắt đầu ca sáng phải nằm trong khoảng từ 00:00 đến 11:59';
    }
    if (morningEnd < '00:00' || morningEnd > '11:59') {
      return 'Giờ kết thúc ca sáng phải nằm trong khoảng từ 00:00 đến 11:59';
    }
    if (morningStart >= morningEnd) {
      return 'Giờ bắt đầu ca sáng phải trước giờ kết thúc ca sáng';
    }

    // Validate Ca chiều: 12:00 - 23:59
    if (!afternoonStart || !afternoonEnd) {
      return 'Vui lòng nhập đầy đủ giờ bắt đầu và giờ kết thúc cho Ca Chiều';
    }
    if (afternoonStart < '12:00' || afternoonStart > '23:59') {
      return 'Giờ bắt đầu ca chiều phải nằm trong khoảng từ 12:00 đến 23:59';
    }
    if (afternoonEnd < '12:00' || afternoonEnd > '23:59') {
      return 'Giờ kết thúc ca chiều phải nằm trong khoảng từ 12:00 đến 23:59';
    }
    if (afternoonStart >= afternoonEnd) {
      return 'Giờ bắt đầu ca chiều phải trước giờ kết thúc ca chiều';
    }

    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setError('');
    setSaving(true);
    try {
      await onSave({
        startDate: weekStartDate,
        morningStartTime: morningStart,
        morningEndTime: morningEnd,
        afternoonStartTime: afternoonStart,
        afternoonEndTime: afternoonEnd,
      });
      onClose();
    } catch (err) {
      setError(err?.message || 'Không thể lưu cấu hình khung giờ ca trực');
    } finally {
      setSaving(false);
    }
  };

  // Format ngày hiển thị tiêu đề
  const formatRangeDisplay = () => {
    if (!weekStartDate || !weekEndDate) return '';
    const [, m1, d1] = weekStartDate.split('-');
    const [, m2, d2] = weekEndDate.split('-');
    return `${d1}/${m1} - ${d2}/${m2}`;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200/90 w-full max-w-4xl lg:max-w-5xl overflow-hidden flex flex-col max-h-[95vh]">
        {/* Header Modal */}
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-white shrink-0">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-teal-50 text-teal-600 border border-teal-100 shadow-2xs">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-slate-900">
                Chỉnh sửa ca trực
              </h2>
              <p className="text-xs text-slate-500">
                Áp dụng cho toàn bộ ca trực của tuần {formatRangeDisplay()}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
            title="Đóng"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-5 sm:p-6 overflow-y-auto space-y-4 text-xs sm:text-[13px]">
          {error && (
            <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 flex items-start gap-2">
              <ShieldAlert className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span className="font-medium">{error}</span>
            </div>
          )}

          {/* Hai ca trực: Ca Sáng & Ca Chiều đặt song song 2 cột ngang */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5">
            {/* Cấu hình Ca Sáng (00:00 - 11:59) */}
            <div className="bg-[#fffdf5] rounded-xl border border-amber-200 p-4 sm:p-4.5 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 rounded-lg bg-amber-100 text-amber-700">
                    <Sun className="w-4 h-4" />
                  </div>
                  <span className="font-bold text-sm sm:text-base text-amber-950">Ca Sáng</span>
                </div>
                {/* Hiển thị thời gian chính xác được chọn */}
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-100/90 text-amber-900 border border-amber-300 shadow-2xs">
                  Khung giờ: {morningStart} - {morningEnd}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-0.5">
                <div>
                  <label className="block text-slate-700 font-semibold mb-1 text-xs">
                    Giờ bắt đầu <span className="text-rose-500">*</span>
                  </label>
                  <TimeWheelPicker
                    value={morningStart}
                    onChange={(val) => {
                      setMorningStart(val);
                      setError('');
                    }}
                    hours={MORNING_HOURS}
                    theme="amber"
                  />
                </div>

                <div>
                  <label className="block text-slate-700 font-semibold mb-1 text-xs">
                    Giờ kết thúc <span className="text-rose-500">*</span>
                  </label>
                  <TimeWheelPicker
                    value={morningEnd}
                    onChange={(val) => {
                      setMorningEnd(val);
                      setError('');
                    }}
                    hours={MORNING_HOURS}
                    theme="amber"
                  />
                </div>
              </div>
            </div>

            {/* Cấu hình Ca Chiều (12:00 - 23:59) */}
            <div className="bg-[#f0f9f8] rounded-xl border border-teal-200 p-4 sm:p-4.5 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 rounded-lg bg-teal-100 text-teal-700">
                    <Sunset className="w-4 h-4" />
                  </div>
                  <span className="font-bold text-sm sm:text-base text-teal-950">Ca Chiều</span>
                </div>
                {/* Hiển thị thời gian chính xác được chọn */}
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-100/90 text-teal-900 border border-teal-300 shadow-2xs">
                  Khung giờ: {afternoonStart} - {afternoonEnd}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-0.5">
                <div>
                  <label className="block text-slate-700 font-semibold mb-1 text-xs">
                    Giờ bắt đầu <span className="text-rose-500">*</span>
                  </label>
                  <TimeWheelPicker
                    value={afternoonStart}
                    onChange={(val) => {
                      setAfternoonStart(val);
                      setError('');
                    }}
                    hours={AFTERNOON_HOURS}
                    theme="teal"
                  />
                </div>

                <div>
                  <label className="block text-slate-700 font-semibold mb-1 text-xs">
                    Giờ kết thúc <span className="text-rose-500">*</span>
                  </label>
                  <TimeWheelPicker
                    value={afternoonEnd}
                    onChange={(val) => {
                      setAfternoonEnd(val);
                      setError('');
                    }}
                    hours={AFTERNOON_HOURS}
                    theme="teal"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Lưu ý quy định khóa ca trực */}
          <div className="p-3.5 sm:p-4 rounded-xl bg-slate-50 border border-slate-200 text-slate-600 space-y-1.5 text-xs sm:text-[12.5px] leading-relaxed">
            <div className="font-bold text-slate-800 flex items-center gap-1.5">
              <Clock className="w-4 h-4 text-teal-600" />
              <span>Quy định tự động khóa chỉnh sửa ca trực:</span>
            </div>
            <p>
              • <strong>Ca sáng:</strong> Chỉ cho phép phân công / chỉnh sửa trong khung giờ từ 00:00 đến 11:59 cùng ngày (trước khi ca trực kết thúc).
            </p>
            <p>
              • <strong>Ca chiều:</strong> Chỉ cho phép phân công / chỉnh sửa trước khi ca trực kết thúc (trong khung giờ từ 12:00 đến 23:59 cùng ngày).
            </p>
            <p>
              • Khi đã quá khung giờ của ca trực theo giờ Việt Nam (<strong>Asia/Ho_Chi_Minh</strong>), hệ thống sẽ tự động khóa toàn bộ thao tác thêm, sửa, xóa phân công ca trực đó.
            </p>
          </div>

          {/* Footer Modal */}
          <div className="pt-3 flex items-center justify-between gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={handleResetDefault}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium transition-colors cursor-pointer text-xs sm:text-sm"
              title="Đặt lại khung giờ mặc định (Sáng: 08:00 - 11:30 & Chiều: 13:00 - 17:30)"
            >
              <RotateCcw className="w-3.5 h-3.5 text-slate-400" />
              <span>Mặc định</span>
            </button>

            <div className="flex items-center gap-2.5">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                className="px-5 py-2 text-xs sm:text-sm font-semibold cursor-pointer"
              >
                Hủy
              </Button>
              <Button
                type="submit"
                variant="mint"
                size="sm"
                disabled={saving}
                className="px-5 py-2 text-xs sm:text-sm font-semibold shadow-sm cursor-pointer inline-flex items-center gap-1.5"
              >
                <Check className="w-3.5 h-3.5" />
                <span>{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</span>
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}

export function DutyShiftConfigModal(props) {
  if (!props.isOpen) return null;
  return (
    <DutyShiftConfigModalContent
      key={`${props.weekStartDate || ''}-${props.initialConfig?.morningStartTime || ''}-${props.initialConfig?.morningEndTime || ''}`}
      {...props}
    />
  );
}

export default DutyShiftConfigModal;
