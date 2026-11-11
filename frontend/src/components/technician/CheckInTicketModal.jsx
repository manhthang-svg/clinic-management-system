import { useEffect, useRef } from 'react';
import { Printer, X, CheckCircle2, Building2, MapPin } from 'lucide-react';

/** Format date and time as HH:mm - DD/MM/YYYY */
function formatTicketTime(date = new Date()) {
  const d = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(d.getTime())) return '';
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  return `${hours}:${minutes} - ${day}/${month}/${year}`;
}

export function CheckInTicketModal({
  isOpen,
  onClose,
  ticketData,
}) {
  const printRef = useRef(null);

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

  if (!isOpen || !ticketData) return null;

  const {
    queueNumber,
    order,
    room,
    issuedAt = new Date(),
  } = ticketData;

  const formattedQueueNum = queueNumber != null
    ? (queueNumber < 10 ? `0${queueNumber}` : `${queueNumber}`)
    : '—';

  const patientName = order?.patientName ? String(order.patientName).toUpperCase() : '—';
  const patientId = order?.patientId || '—';

  // Tuổi, năm sinh, giới tính
  const age = order?.age;
  const birthYear = order?.birthYear || (age ? new Date().getFullYear() - age : null);
  const gender = order?.gender || '—';
  const ageGenderStr = birthYear
    ? `${birthYear} (${age ? `${age} tuổi` : ''}) - ${gender}`
    : (age ? `${age} tuổi - ${gender}` : gender);

  // Loại lượt khám (BHYT hoặc Viện phí / Dịch vụ)
  const insuranceType = order?.insuranceType === 'BHYT'
    ? 'BHYT'
    : (order?.insuranceType || 'Viện phí');

  // Độ ưu tiên
  const isPriority = order?.priority === 'PRIORITY';

  // Danh sách dịch vụ cần thực hiện tại phòng này
  const services = Array.isArray(order?.roomServices) && order.roomServices.length > 0
    ? order.roomServices
    : (order?.serviceName ? [{ serviceName: order.serviceName, serviceCode: order.serviceCode }] : []);

  // Mã phiếu chỉ định
  const orderCode = order?.orderCode?.replace(/-\d+$/, '') || order?.orderCode || '—';

  // Bác sĩ & Phòng gửi chỉ định
  const doctorName = order?.doctorName || '—';
  const doctorRoom = order?.doctorRoom || '';
  const doctorAndRoom = doctorRoom ? `${doctorName} - ${doctorRoom}` : doctorName;

  // Phòng máy & Địa điểm thực hiện
  const roomCode = room?.code || order?.roomCode || '';
  const roomName = room?.name || order?.roomName || 'Phòng Cận lâm sàng';
  const roomFullName = roomCode ? `${roomName} - ${roomCode}` : roomName;
  const roomLocation = room?.location || 'Tầng 1 - Khu Cận lâm sàng';

  const timeString = formatTicketTime(issuedAt);

  const handlePrint = () => {
    window.setTimeout(() => {
      window.print();
    }, 50);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-xs animate-in fade-in duration-200 print:static print:inset-auto print:p-0 print:m-0 print:bg-transparent print:backdrop-filter-none print:filter-none print:transform-none print:block print:w-auto print:h-auto">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="checkin-ticket-title"
        className="relative w-full max-w-2xl max-h-[95vh] flex flex-col rounded-2xl bg-white shadow-2xl border border-slate-200 overflow-hidden print:static print:inset-auto print:m-0 print:p-0 print:w-full print:max-w-none print:max-h-none print:shadow-none print:border-none print:overflow-visible print:bg-transparent print:block"
      >
        {/* Header điều khiển trên Modal */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-3 bg-slate-50/80 shrink-0 print:hidden">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-emerald-600" />
            <h3 id="checkin-ticket-title" className="font-bold text-sm text-slate-800">
              Đã cấp số thứ tự thành công
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng hộp thoại"
            className="rounded-lg p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 transition-colors cursor-pointer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Nội dung phiếu in - Tối ưu 2 cột ngang rộng rãi trên màn hình, khi in xếp dọc chuẩn phiếu 80mm */}
        <div className="p-5 overflow-y-auto print:p-0 print:m-0 print:overflow-visible print:block">
          <article
            ref={printRef}
            aria-label="Phiếu số thứ tự cận lâm sàng"
            className="checkin-ticket-print bg-white rounded-xl border border-dashed border-slate-300 p-5 text-xs text-slate-800 space-y-3.5 shadow-2xs print:border-dashed print:border-slate-400 print:p-3 print:space-y-3 print:shadow-none print:text-black"
          >
            {/* Header phòng khám & tiêu đề */}
            <header className="text-center border-b border-dashed border-slate-200 pb-2.5 print:border-slate-300">
              <p className="text-xs font-black uppercase tracking-wider text-teal-800 print:text-black">
                PHÒNG KHÁM ĐA KHOA METPH
              </p>
              <h2 className="text-sm font-extrabold uppercase text-slate-900 mt-0.5 print:text-black">
                PHIẾU SỐ THỨ TỰ CẬN LÂM SÀNG
              </h2>
              <p className="text-[11px] text-slate-500 mt-0.5 print:text-black">
                Thời gian cấp: <span className="font-semibold text-slate-700 print:text-black">{timeString}</span>
              </p>
            </header>

            {/* Thân phiếu: 2 Cột song song rộng rãi trên màn hình, xếp dọc liên tiếp khi in */}
            <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-stretch print:block print:space-y-3">
              {/* Cột trái: Khối STT to đúp & Địa điểm phòng */}
              <section className="md:col-span-5 flex flex-col justify-center text-center p-3.5 bg-teal-50/70 rounded-xl border border-teal-100/90 print:p-2.5 print:bg-transparent print:border-dashed print:border-slate-400">
                <p className="text-[11px] font-bold uppercase tracking-wider text-teal-700 print:text-black">
                  SỐ THỨ TỰ CỦA BẠN
                </p>
                <div className="text-6xl font-black text-[#0d9488] leading-none my-2 font-mono tracking-tight print:text-black">
                  {formattedQueueNum}
                </div>
                <div className="pt-1 space-y-0.5">
                  <div className="inline-flex items-center justify-center gap-1.5 text-xs font-bold text-slate-900 leading-snug print:text-black">
                    <Building2 className="w-3.5 h-3.5 text-teal-600 shrink-0 print:hidden" />
                    <span>{roomFullName}</span>
                  </div>
                  {roomLocation && (
                    <div className="flex items-center justify-center gap-1 text-[11px] text-slate-500 print:text-black">
                      <MapPin className="w-3 h-3 text-slate-400 shrink-0 print:hidden" />
                      <span>{roomLocation}</span>
                    </div>
                  )}
                </div>
              </section>

              {/* Cột phải: Thông tin định danh & Dịch vụ chỉ định */}
              <div className="md:col-span-7 flex flex-col justify-between space-y-2.5 print:space-y-2.5">
                {/* 2. Thông tin định danh bệnh nhân */}
                <section className="space-y-1.5 bg-slate-50/70 p-2.5 rounded-lg border border-slate-100 print:bg-transparent print:border-dashed print:border-slate-300 print:p-2">
                  <div className="flex justify-between items-baseline gap-2">
                    <span className="text-slate-500 shrink-0 print:text-black">Họ và tên:</span>
                    <span className="font-extrabold text-sm text-slate-900 text-right print:text-black">{patientName}</span>
                  </div>
                  <div className="flex justify-between items-center gap-2 text-xs">
                    <span className="text-slate-500 print:text-black">Mã bệnh nhân:</span>
                    <span className="font-mono font-bold text-slate-800 print:text-black">{patientId}</span>
                  </div>
                  <div className="flex justify-between items-center gap-2 text-xs">
                    <span className="text-slate-500 print:text-black">Năm sinh / Giới tính:</span>
                    <span className="font-semibold text-slate-800 print:text-black">{ageGenderStr}</span>
                  </div>
                  <div className="flex justify-between items-center gap-2 text-xs">
                    <span className="text-slate-500 print:text-black">Đối tượng:</span>
                    <span className="font-bold text-teal-800 bg-teal-50 px-2 py-0.5 rounded border border-teal-200/80 print:bg-transparent print:text-black print:border-slate-400">
                      {insuranceType}
                    </span>
                  </div>
                  {/* (Nếu có) Độ ưu tiên: chỉ hiện khi có ưu tiên */}
                  {isPriority && (
                    <div className="flex justify-between items-center gap-2 text-xs pt-0.5">
                      <span className="text-rose-600 font-bold print:text-black">Độ ưu tiên:</span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10.5px] font-extrabold bg-rose-50 text-rose-700 border border-rose-200 print:bg-transparent print:text-black print:border-slate-400">
                        ƯU TIÊN (Xử lý trước)
                      </span>
                    </div>
                  )}
                </section>

                {/* 3. Thông tin dịch vụ & Chỉ định */}
                <section className="space-y-1.5 pt-1.5 border-t border-dashed border-slate-200 print:pt-2 print:border-slate-300">
                  <div>
                    <span className="text-slate-500 block text-[11px] font-medium mb-1 print:text-black">
                      Dịch vụ cần thực hiện tại phòng:
                    </span>
                    <ul className="space-y-1 pl-1">
                      {services.map((svc, index) => (
                        <li key={svc.id || index} className="flex items-start gap-1.5 text-xs text-slate-800 print:text-black">
                          <span className="text-teal-600 font-bold leading-tight print:text-black">•</span>
                          <span className="font-semibold">{svc.serviceName || svc.tenchidinh || 'Dịch vụ kỹ thuật'}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div className="flex justify-between items-center gap-2 text-[11px] pt-0.5">
                    <span className="text-slate-500 print:text-black">Mã phiếu chỉ định:</span>
                    <span className="font-mono font-bold text-slate-700 print:text-black">{orderCode}</span>
                  </div>

                  <div className="flex justify-between items-baseline gap-2 text-[11px]">
                    <span className="text-slate-500 shrink-0 print:text-black">Bác sĩ & Phòng gửi:</span>
                    <span className="font-semibold text-slate-700 text-right print:text-black">{doctorAndRoom}</span>
                  </div>
                </section>
              </div>
            </div>

            {/* Dòng ghi chú / Lời dặn (Footer) */}
            <footer className="border-t border-dashed border-slate-200 pt-2.5 text-center space-y-0.5 print:border-slate-300">
              <p className="text-[11px] font-semibold text-slate-700 leading-snug print:text-black">
                “Quý khách vui lòng chú ý lắng nghe loa và nhìn màn hình hiển thị để vào phòng khi đến lượt.”
              </p>
              <p className="text-[10px] italic text-slate-400 print:text-slate-600">
                “Phiếu chỉ có giá trị trong ngày khám.”
              </p>
            </footer>
          </article>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-2.5 border-t border-slate-100 px-6 py-3 bg-slate-50/80 shrink-0 print:hidden">
          <button
            type="button"
            onClick={onClose}
            className="h-9 px-4 inline-flex items-center text-xs font-bold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 rounded-xl transition-all cursor-pointer shadow-2xs"
          >
            Đóng
          </button>
          <button
            type="button"
            onClick={handlePrint}
            className="h-9 px-4 inline-flex items-center gap-1.5 text-xs font-bold text-white bg-teal-600 hover:bg-teal-700 rounded-xl transition-all cursor-pointer shadow-sm active:scale-95"
          >
            <Printer className="w-4 h-4" />
            <span>In phiếu số thứ tự</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default CheckInTicketModal;
