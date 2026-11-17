import { CheckCircle2, Printer, Users } from 'lucide-react';
import { PRIORITY_REASON_LABELS, VISIT_STATUS_LABELS, VISIT_TYPE_LABELS } from '../../constants/visitData';
import { Barcode } from '../paraclinicalResult/Barcode';
import { PrintTime } from './PrintTime';
import { ageText, formatDate, formatDateTime, formatMoney, needsPaymentGuide } from './visitFormat';

function Row({ label, children }) {
  return (
    <div className="flex justify-between gap-4 py-1.5">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-800 font-semibold text-right">{children}</dd>
    </div>
  );
}

function ticketGuidance(ticket, loading) {
  if (!ticket) {
    return loading ? 'Đang tải dữ liệu phiếu khám…' : 'Chưa thể xác định hướng dẫn thanh toán.';
  }
  // BR-15: an insured patient waits at once and pays everything after the conclusion.
  if (ticket.visitType === 'BHYT') {
    return 'Hướng dẫn bệnh nhân đến khu chờ của phòng khám. Phí khám được thanh toán tại quầy thu ngân sau khi có kết luận.';
  }
  if (needsPaymentGuide(ticket)) {
    return 'Đưa phiếu hướng dẫn thanh toán cho bệnh nhân. Bệnh nhân thanh toán phí khám tại quầy thu ngân và nhận phiếu khám có số thứ tự tại đó.';
  }
  if (ticket.patientAmount > 0) {
    return 'Phí khám đã được thanh toán – hướng dẫn bệnh nhân đến khu chờ của phòng khám.';
  }
  return 'Phí khám đã được chi trả toàn bộ – hướng dẫn bệnh nhân đến khu chờ của phòng khám.';
}

/**
 * UC-41 Print Examination Ticket (BR-16), shown after UC-40 succeeds.
 * TBD: ticket printer integration (HW-01); "In phiếu khám" uses the browser print dialog.
 */
export function VisitTicket({
  visit, ticket, loading, error, onReload, onPrint, onDone, title = 'Đăng ký khám thành công',
}) {
  const guidance = ticketGuidance(ticket, loading);
  const paymentGuide = needsPaymentGuide(ticket);

  return (
    <div className="space-y-5">
      {/* While this screen is open, the browser prints on one A5 page. */}
      <style>{'@media print { @page { size: A4 portrait; margin: 0; } }'}</style>

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3 min-w-0 flex-1">
          <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <div className="min-w-0">
            <h2 className="text-lg! leading-7! font-bold! text-[#1b2533]">{title}</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              Lượt khám <span className="font-mono font-semibold text-slate-700">{visit.sovaovien}</span> ở trạng thái{' '}
              <span className="font-semibold text-slate-700">{VISIT_STATUS_LABELS[visit.status] ?? visit.status}</span>.
            </p>
            <p className="text-sm text-slate-600 mt-2">{guidance}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-3 shrink-0">
          <button
            type="button"
            onClick={onDone}
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
          >
            <Users className="w-4 h-4" />
            Về danh sách bệnh nhân
          </button>
          <button
            type="button"
            onClick={onPrint}
            disabled={!ticket || loading}
            className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <Printer className="w-4 h-4" />
            {paymentGuide ? 'In phiếu hướng dẫn thanh toán' : 'In phiếu khám'}
          </button>
        </div>
        {error && (
          <div role="alert" className="basis-full p-3 rounded-xl bg-rose-50 border border-rose-200 text-sm text-rose-700">
            {error}{' '}
            <button type="button" className="font-semibold underline cursor-pointer" onClick={onReload}>Tải lại phiếu</button>
          </div>
        )}
      </section>

      {ticket && (
        <div className="flex justify-center rounded-2xl bg-slate-100 px-2 py-6 sm:px-6 sm:py-8">
          {paymentGuide ? <PaymentGuideA5 ticket={ticket} /> : <VisitTicketA5 ticket={ticket} />}
        </div>
      )}
    </div>
  );
}

function A5Row({ label, wide = false, labelWidth = 'w-28', children }) {
  return (
    <div className={`flex gap-2 py-1 ${wide ? 'sm:col-span-2 print:col-span-2' : ''}`}>
      <dt className={`${labelWidth} shrink-0 whitespace-nowrap text-slate-500`}>{label}:</dt>
      <dd className="min-w-0 flex-1 font-semibold text-slate-900 break-words">{children}</dd>
    </div>
  );
}

/** Full date of birth; the birth year alone when the date is not sent. */
function birthText(ticket) {
  return ticket.dateOfBirth ? formatDate(ticket.dateOfBirth) : (ticket.birthYear ?? '—');
}

function cardValidity(ticket) {
  if (!ticket.insuranceValidFrom && !ticket.insuranceValidTo) return '—';
  return `${ticket.insuranceValidFrom ? formatDate(ticket.insuranceValidFrom) : '…'} – ${
    ticket.insuranceValidTo ? formatDate(ticket.insuranceValidTo) : '…'}`;
}

function PaymentNote({ ticket }) {
  const payable = typeof ticket?.patientAmount === 'number' ? ticket.patientAmount : null;
  // BR-16: only a self-paid ticket shows the fee to pay before waiting.
  if (ticket.visitType === 'BHYT') {
    return <p className="font-semibold text-emerald-700">Mời đến khu chờ – thanh toán sau khi có kết luận</p>;
  }
  if (payable === 0) return <p className="font-semibold text-emerald-700">Phí khám đã được chi trả toàn bộ – mời đến khu chờ</p>;
  if (ticket.queueNumber != null) return <p className="font-semibold text-emerald-700">Đã thanh toán phí khám – mời đến khu chờ</p>;
  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-slate-700">Thanh toán tại quầy thu ngân trước khi vào khu chờ</p>
        {payable !== null && <p className="text-lg font-extrabold text-slate-900">{formatMoney(payable)}</p>}
      </div>
      {/* I1-D48: the queue number is issued with the payment. */}
      <p className="text-[12px] italic text-slate-500">Sau khi thanh toán sẽ nhận số thứ tự khám tại quầy thu ngân.</p>
    </div>
  );
}

/**
 * UC-41 AF: payment guide for a Dịch vụ visit that has not paid the examination fee. It is not an examination
 * ticket: it has no queue number and cannot be used to enter the examination room.
 * ASSUMPTION: the cashier counter has no recorded location yet, so the guide names "Quầy thu ngân".
 */
export function PaymentGuideA5({ ticket }) {
  const registeredAt = ticket.registeredAt ? new Date(ticket.registeredAt) : null;
  return (
    <article
      aria-label="Phiếu hướng dẫn thanh toán"
      className="visit-ticket-a5-print relative flex w-full max-w-[148mm] flex-col bg-white text-[13px] leading-5 text-slate-800 shadow-[0_8px_30px_rgba(15,23,42,0.12)] ring-1 ring-slate-200 p-[9mm] sm:aspect-[148/210]"
    >
      <PrintTime className="absolute left-[9mm] top-[3.5mm]" />
      <header className="flex items-start justify-between gap-4 border-b-2 border-slate-800 pb-3">
        <div className="min-w-0">
          <p className="font-bold uppercase leading-tight text-slate-900">{ticket.facilityName}</p>
          {ticket.facilityAddress && <p className="mt-0.5 text-[11px] text-slate-500">{ticket.facilityAddress}</p>}
        </div>
        <div className="shrink-0 text-right text-[11px] text-slate-600">
          <p>Mã lượt khám</p>
          {/* Barcode of the registration code (Code 128), with the code printed under it. */}
          {ticket.registrationCode
            ? <Barcode value={ticket.registrationCode} height={28} module={1} />
            : <p className="font-mono text-[13px] font-bold text-slate-900">—</p>}
        </div>
      </header>

      <div className="mt-4 text-center">
        <h3 className="text-xl! font-extrabold! uppercase tracking-wide text-slate-900">Phiếu hướng dẫn thanh toán</h3>
        <p className="text-xs text-slate-500">Phí khám bệnh – lượt khám dịch vụ</p>
      </div>

      <div className="mt-5 rounded-xl border-2 border-[#127e7a] px-4 py-3 text-center">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-[#127e7a]">Số tiền cần thanh toán</p>
        <p className="text-3xl font-extrabold text-[#127e7a]">{formatMoney(ticket.patientAmount)}</p>
        <p className="mt-1 text-xs font-semibold text-slate-700">Nơi thanh toán: Quầy thu ngân</p>
      </div>

      <dl className="mt-5">
        <A5Row label="Họ và tên"><span className="uppercase">{ticket.patientName}</span></A5Row>
        <A5Row label="Mã bệnh nhân"><span className="font-mono">{ticket.patientCode}</span></A5Row>
        <A5Row label="Ngày sinh">{birthText(ticket)}</A5Row>
        <A5Row label="Phòng khám">
          {ticket.roomName ?? '—'}
          {ticket.roomLocation && <span className="block text-[11px] font-normal text-slate-500">{ticket.roomLocation}</span>}
        </A5Row>
        <A5Row label="Thời điểm đăng ký">{formatDateTime(ticket.registeredAt)}</A5Row>
      </dl>

      <p role="note" className="mt-5 rounded-lg border-2 border-dashed border-rose-400 bg-rose-50 px-3 py-2.5 text-center text-[13px] font-bold text-rose-700">
        Chưa có số thứ tự. Phiếu này không dùng để vào khám.
        <span className="mt-1 block text-[12px] font-semibold text-rose-600">
          Số thứ tự và phiếu khám được cấp tại quầy thu ngân sau khi thanh toán.
        </span>
      </p>

      <footer className="mt-auto flex justify-end pt-6">
        <div className="w-48 text-center text-[12px]">
          <p className="italic text-slate-600">
            {registeredAt
              ? `Ngày ${registeredAt.getDate()} tháng ${registeredAt.getMonth() + 1} năm ${registeredAt.getFullYear()}`
              : 'Ngày … tháng … năm …'}
          </p>
          <p className="mt-1 font-bold uppercase text-slate-800">Người tiếp đón</p>
          <p className="mt-2 font-semibold text-slate-900">{ticket.registeredByName || '—'}</p>
        </div>
      </footer>
    </article>
  );
}

/** UC-41 examination ticket on an A5 sheet (148 × 210 mm), as handed to the patient. */
export function VisitTicketA5({ ticket }) {
  const registeredAt = ticket.registeredAt ? new Date(ticket.registeredAt) : null;
  const visitTypeText = `${VISIT_TYPE_LABELS[ticket.visitType] ?? '—'}${
    ticket.visitType === 'BHYT' && ticket.coverageRate != null ? ` · Mức hưởng ${ticket.coverageRate}%` : ''}`;

  return (
    <article
      aria-label="Phiếu khám bệnh"
      className="visit-ticket-a5-print relative flex w-full max-w-[148mm] flex-col bg-white text-[13px] leading-5 text-slate-800 shadow-[0_8px_30px_rgba(15,23,42,0.12)] ring-1 ring-slate-200 p-[9mm] sm:aspect-[148/210]"
    >
      <PrintTime className="absolute left-[9mm] top-[3.5mm]" />
      <header className="flex items-start justify-between gap-4 border-b-2 border-slate-800 pb-3">
        <div className="min-w-0">
          <p className="font-bold uppercase leading-tight text-slate-900">{ticket.facilityName}</p>
          {ticket.facilityAddress && <p className="mt-0.5 text-[11px] text-slate-500">{ticket.facilityAddress}</p>}
        </div>
        <div className="shrink-0 text-right text-[11px] text-slate-600">
          <p>Mã lượt khám</p>
          {/* Barcode of the registration code (Code 128), with the code printed under it. */}
          {ticket.registrationCode
            ? <Barcode value={ticket.registrationCode} height={28} module={1} />
            : <p className="font-mono text-[13px] font-bold text-slate-900">—</p>}
        </div>
      </header>

      <div className="mt-4 text-center">
        <h3 className="text-xl! font-extrabold! uppercase tracking-wide text-slate-900">Phiếu khám bệnh</h3>
      </div>

      {/* I1-D48: a visit that must pay first gets its number with the receipt, so its ticket shows none. */}
      {(ticket.queueNumber != null || ticket.priority) && (
        <div className="mt-4 flex flex-col items-center gap-2">
          {ticket.queueNumber != null && (
            <div className="min-w-36 rounded-xl border-2 border-[#127e7a] px-6 py-2 text-center">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-[#127e7a]">Số thứ tự</p>
              <p className="text-6xl font-extrabold leading-none text-[#127e7a]">{ticket.queueNumber}</p>
            </div>
          )}
          {ticket.priority && (
            <span className="rounded-md border border-rose-300 bg-rose-50 px-2.5 py-0.5 text-xs font-bold text-rose-700">
              ƯU TIÊN · {PRIORITY_REASON_LABELS[ticket.priorityReason] ?? ''}
            </span>
          )}
        </div>
      )}

      <section className="mt-5">
        <h4 className="mb-1 text-[11px] font-bold uppercase tracking-wider text-slate-500">I. Thông tin bệnh nhân</h4>
        <dl className="grid grid-cols-1 gap-x-4 sm:grid-cols-2 print:grid-cols-2">
          <A5Row label="Họ và tên" wide><span className="uppercase">{ticket.patientName}</span></A5Row>
          <A5Row label="Mã bệnh nhân"><span className="font-mono">{ticket.patientCode}</span></A5Row>
          <A5Row label="Giới tính">{ticket.gender ?? '—'}</A5Row>
          <A5Row label="Ngày sinh">{birthText(ticket)}</A5Row>
          <A5Row label="Tuổi">
            {ageText(ticket.dateOfBirth, ticket.registeredAt ? new Date(ticket.registeredAt) : new Date())}
          </A5Row>
          <A5Row label="Điện thoại">{ticket.phone || '—'}</A5Row>
          <A5Row label="Dân tộc">{ticket.ethnicity || '—'}</A5Row>
          <A5Row label="Nghề nghiệp" wide>{ticket.occupation || '—'}</A5Row>
          <A5Row label="Địa chỉ" wide>{ticket.address || '—'}</A5Row>
          <A5Row label="Đối tượng" wide>{visitTypeText}</A5Row>
          {ticket.visitType === 'BHYT' && (
            <>
              <A5Row label="Số thẻ BHYT"><span className="font-mono">{ticket.insuranceCardNumber || '—'}</span></A5Row>
              <A5Row label="Hạn thẻ">{cardValidity(ticket)}</A5Row>
            </>
          )}
        </dl>
      </section>

      <section className="mt-4">
        <h4 className="mb-1 text-[11px] font-bold uppercase tracking-wider text-slate-500">II. Nơi khám</h4>
        <dl>
          <A5Row labelWidth="w-36" label="Phòng khám">{ticket.roomName ?? '—'}</A5Row>
          <A5Row labelWidth="w-36" label="Vị trí">{ticket.roomLocation || '—'}</A5Row>
          <A5Row labelWidth="w-36" label="Bác sĩ">{ticket.doctorNames?.join(', ') || 'Chưa phân công'}</A5Row>
          <A5Row labelWidth="w-36" label="Thời điểm đăng ký">{formatDateTime(ticket.registeredAt)}</A5Row>
          {ticket.reasonForVisit && <A5Row labelWidth="w-36" label="Lý do khám">{ticket.reasonForVisit}</A5Row>}
        </dl>
      </section>

      <div className="mt-4 rounded-lg border border-dashed border-slate-400 px-3 py-2.5">
        <PaymentNote ticket={ticket} />
      </div>

      <footer className="mt-auto flex justify-end pt-6">
        <div className="w-48 text-center text-[12px]">
          <p className="italic text-slate-600">
            {registeredAt
              ? `Ngày ${registeredAt.getDate()} tháng ${registeredAt.getMonth() + 1} năm ${registeredAt.getFullYear()}`
              : 'Ngày … tháng … năm …'}
          </p>
          <p className="mt-1 font-bold uppercase text-slate-800">Người tiếp đón</p>
          <p className="mt-2 font-semibold text-slate-900">{ticket.registeredByName || '—'}</p>
        </div>
      </footer>
    </article>
  );
}

/** UC-41 printable ticket (BR-16); also the queue-number ticket the Cashier prints after payment (I1-D48). */
export function VisitTicketSheet({ ticket }) {
  const payable = typeof ticket?.patientAmount === 'number' ? ticket.patientAmount : null;
  return (
    <article aria-label="Phiếu khám bệnh" className="visit-ticket-print bg-white rounded-2xl border-2 border-dashed border-slate-300 p-6 text-sm">
      <header className="text-center border-b border-dashed border-slate-200 pb-4">
        <p className="text-sm font-bold uppercase text-slate-800">{ticket.facilityName}</p>
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Phiếu khám bệnh</p>
        <p className="text-xs text-slate-400 font-mono mt-1">{ticket.registrationCode}</p>
        {/* I1-D48: a visit that must pay first gets its number with the receipt, so its ticket shows none. */}
        {ticket.queueNumber != null && (
          <>
            <p className="text-[11px] text-slate-500 mt-3">Số thứ tự</p>
            <p className="text-6xl font-extrabold text-[#127e7a] leading-none mt-1">{ticket.queueNumber}</p>
          </>
        )}
        {ticket.priority && (
          <span className="inline-flex mt-3 px-2.5 py-0.5 rounded-lg text-xs font-bold bg-rose-50 text-rose-600 border border-rose-200">
            ƯU TIÊN · {PRIORITY_REASON_LABELS[ticket.priorityReason] ?? ''}
          </span>
        )}
      </header>
      <dl className="pt-3">
        <Row label="Bệnh nhân">{ticket.patientName}</Row>
        <Row label="Mã BN"><span className="font-mono">{ticket.patientCode}</span></Row>
        <Row label="Năm sinh / Giới tính">{ticket.birthYear ?? '—'} · {ticket.gender ?? '—'}</Row>
        <Row label="Phòng khám">{ticket.roomName ?? '—'}</Row>
        <Row label="Vị trí">{ticket.roomLocation || '—'}</Row>
        <Row label="Bác sĩ">{ticket.doctorNames?.join(', ') || 'Chưa phân công'}</Row>
        <Row label="Loại lượt khám">
          {VISIT_TYPE_LABELS[ticket.visitType]}
          {ticket.visitType === 'BHYT' && ticket.coverageRate != null && ` · ${ticket.coverageRate}%`}
        </Row>
        <Row label="Thời điểm đăng ký">{formatDateTime(ticket.registeredAt)}</Row>
      </dl>
      <footer className="mt-3 pt-3 border-t border-dashed border-slate-200 text-center">
        {/* BR-16: only a self-paid ticket shows the fee to pay before waiting. */}
        {ticket.visitType === 'BHYT' ? (
          <p className="font-semibold text-emerald-700">Mời đến khu chờ – thanh toán sau khi có kết luận</p>
        ) : payable === 0 ? (
          <p className="font-semibold text-emerald-700">Phí khám đã được chi trả toàn bộ – mời đến khu chờ</p>
        ) : ticket.queueNumber != null ? (
          <p className="font-semibold text-emerald-700">Đã thanh toán phí khám – mời đến khu chờ</p>
        ) : (
          <>
            {payable !== null && <p className="text-lg font-extrabold text-slate-800">{formatMoney(payable)}</p>}
            <p className="text-xs text-slate-600 mt-1">Thanh toán tại quầy thu ngân trước khi vào khu chờ</p>
          </>
        )}
      </footer>
    </article>
  );
}
