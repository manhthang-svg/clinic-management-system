import { CheckCircle2, Printer, Users } from 'lucide-react';
import { PRIORITY_REASON_LABELS, VISIT_STATUS_LABELS, VISIT_TYPE_LABELS } from '../../constants/visitData';
import { formatDateTime, formatMoney, genderLabel } from './visitFormat';

function Row({ label, children }) {
  return (
    <div className="flex justify-between gap-4 py-1.5">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-800 font-semibold text-right">{children}</dd>
    </div>
  );
}

/**
 * UC-41 Print Examination Ticket (BR-16), shown after UC-40 succeeds.
 * TBD: ticket printer integration (HW-01); "In phiếu khám" uses the browser print dialog.
 */
export function VisitTicket({ visit, patient, room, coverageRate, fee, onPrint, onDone }) {
  const insured = visit.doituongbnLoai === 'BHYT';
  const payable = typeof fee?.patientAmount === 'number' ? fee.patientAmount : null;
  const yearOfBirth = patient.ngaysinh ? patient.ngaysinh.slice(0, 4) : '—';

  return (
    <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_380px] gap-5 items-start">
      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-6 space-y-4">
        <div className="flex items-start gap-3">
          <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-lg! leading-7! font-bold! text-[#1b2533]">Đăng ký khám thành công</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              Lượt khám <span className="font-mono font-semibold text-slate-700">{visit.sovaovien}</span> ở trạng thái{' '}
              <span className="font-semibold text-slate-700">{VISIT_STATUS_LABELS[visit.status] ?? visit.status}</span>.
            </p>
          </div>
        </div>
        <p className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 text-sm text-slate-600">
          {insured
            ? 'Hướng dẫn bệnh nhân BHYT đến thẳng khu chờ của phòng khám.'
            : 'Đưa phiếu cho bệnh nhân và hướng dẫn ra quầy thu ngân thanh toán phí khám trước khi vào khu chờ.'}
        </p>
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={onPrint}
            className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer"
          >
            <Printer className="w-4 h-4" />
            In phiếu khám
          </button>
          <button
            type="button"
            onClick={onDone}
            className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
          >
            <Users className="w-4 h-4" />
            Về danh sách bệnh nhân
          </button>
        </div>
      </section>

      <article aria-label="Phiếu khám bệnh" className="bg-white rounded-2xl border-2 border-dashed border-slate-300 p-6 text-sm">
        <header className="text-center border-b border-dashed border-slate-200 pb-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Phiếu khám bệnh</p>
          <p className="text-xs text-slate-400 font-mono mt-1">{visit.sovaovien}</p>
          <p className="text-[11px] text-slate-500 mt-3">Số thứ tự</p>
          <p className="text-6xl font-extrabold text-[#127e7a] leading-none mt-1">{visit.queueNumber}</p>
          {visit.isPriority && (
            <span className="inline-flex mt-3 px-2.5 py-0.5 rounded-lg text-xs font-bold bg-rose-50 text-rose-600 border border-rose-200">
              ƯU TIÊN · {PRIORITY_REASON_LABELS[visit.priorityReason] ?? ''}
            </span>
          )}
        </header>
        <dl className="pt-3">
          <Row label="Bệnh nhân">{patient.hoten}</Row>
          <Row label="Mã BN"><span className="font-mono">{patient.mabenhnhan}</span></Row>
          <Row label="Năm sinh / Giới tính">{yearOfBirth} · {genderLabel(patient)}</Row>
          <Row label="Phòng khám">{visit.roomName ?? room?.name ?? '—'}</Row>
          <Row label="Vị trí">{room?.location || '—'}</Row>
          <Row label="Loại lượt khám">
            {VISIT_TYPE_LABELS[visit.doituongbnLoai]}
            {insured && coverageRate !== null && ` · ${coverageRate}%`}
          </Row>
          <Row label="Thời điểm đăng ký">{formatDateTime(visit.thoigianvaovien)}</Row>
        </dl>
        <footer className="mt-3 pt-3 border-t border-dashed border-slate-200 text-center">
          {payable === 0 ? (
            <p className="font-semibold text-emerald-700">Đã được miễn phí khám – mời đến khu chờ</p>
          ) : insured ? (
            <p className="font-semibold text-slate-700">Mời đến khu chờ của phòng khám</p>
          ) : (
            <>
              {payable !== null && <p className="text-lg font-extrabold text-slate-800">{formatMoney(payable)}</p>}
              <p className="text-xs text-slate-600 mt-1">Thanh toán tại quầy thu ngân trước khi vào khu chờ</p>
            </>
          )}
        </footer>
      </article>
    </div>
  );
}
