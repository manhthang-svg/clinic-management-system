import { formatDateTime, formatMoney } from '../visits/visitFormat';
import { VISIT_TYPE_LABELS } from '../../constants/visitData';

const TYPE_LABEL = { EXAMINATION_FEE: 'Phí khám bệnh', PARACLINICAL_FEE: 'Phí cận lâm sàng' };
const METHOD_LABEL = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', CARD: 'Thẻ' };

// UC-72: mau in phieu thu (in qua hop thoai in cua trinh duyet)
export function PaymentReceipt({ receipt }) {
    const payment = receipt.payment;
    const paraclinical = payment.type === 'PARACLINICAL_FEE';
    const visitType = VISIT_TYPE_LABELS[receipt.visitType] || receipt.visitType;

    return (
        <article aria-label="Phiếu thu" className="payment-receipt-print rounded-2xl border border-slate-200 bg-white p-6 text-xs text-slate-900">
            <header className="text-center">
                <p className="text-sm font-bold uppercase">{receipt.facilityName || 'Phòng khám'}</p>
                <h2 className="mt-1 text-base font-extrabold uppercase">Phiếu thu – {TYPE_LABEL[payment.type]}</h2>
                <p className="mt-0.5">Số phiếu: <strong className="font-mono">{payment.receiptNumber}</strong></p>
                {receipt.reprint && <p className="mt-1 font-extrabold text-rose-700">BẢN SAO</p>}
            </header>

            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-1">
                <dt className="text-slate-500">Bệnh nhân</dt><dd className="font-semibold">{payment.patientName}</dd>
                <dt className="text-slate-500">Mã bệnh nhân</dt><dd className="font-mono">{payment.patientCode}</dd>
                <dt className="text-slate-500">Mã lượt khám</dt><dd className="font-mono">{payment.sovaovien}</dd>
                <dt className="text-slate-500">Đối tượng</dt>
                <dd>{visitType}{receipt.coverageRate != null ? ` – mức hưởng ${receipt.coverageRate}%` : ''}</dd>
                {!paraclinical && (
                    <>
                        <dt className="text-slate-500">Phòng khám</dt>
                        <dd>{payment.roomName}{receipt.visitQueueNumber != null ? ` – STT ${receipt.visitQueueNumber}` : ''}</dd>
                    </>
                )}
            </dl>

            <table className="mt-4 w-full border-collapse">
                <thead>
                    <tr className="border-b border-slate-300 text-left">
                        <th className="py-1 pr-2">Dịch vụ</th>
                        <th className="py-1 pr-2 text-right">Đơn giá</th>
                        <th className="py-1 pr-2 text-right">BHYT trả</th>
                        <th className="py-1 text-right">BN trả</th>
                    </tr>
                </thead>
                <tbody>
                    {payment.items.map((item) => (
                        <tr key={item.id} className="border-b border-slate-100 align-top">
                            <td className="py-1 pr-2">
                                {item.serviceName}
                                {paraclinical && (
                                    <span className="block text-[11px] text-slate-600">
                                        {item.roomName
                                            ? `${item.roomName}${item.roomLocation ? ` (${item.roomLocation})` : ''}${item.queueNumber ? ` – STT ${item.queueNumber}` : ''}`
                                            : 'Chưa có phòng thực hiện'}
                                    </span>
                                )}
                            </td>
                            <td className="py-1 pr-2 text-right tabular-nums">{formatMoney(item.unitPrice)}</td>
                            <td className="py-1 pr-2 text-right tabular-nums">{formatMoney(item.bhytChitra)}</td>
                            <td className="py-1 text-right tabular-nums">{formatMoney(item.patientAmount)}</td>
                        </tr>
                    ))}
                </tbody>
                <tfoot>
                    <tr className="font-bold">
                        <td className="pt-2" colSpan={2}>Tổng cộng {formatMoney(payment.totalAmount)}</td>
                        <td className="pt-2 text-right tabular-nums">{formatMoney(payment.insuranceTotalAmount)}</td>
                        <td className="pt-2 text-right tabular-nums" data-testid="receipt-patient-total">{formatMoney(payment.patientTotalAmount)}</td>
                    </tr>
                </tfoot>
            </table>

            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-1">
                <dt className="text-slate-500">Phương thức</dt><dd>{METHOD_LABEL[payment.paymentMethod] || payment.paymentMethod}</dd>
                <dt className="text-slate-500">Thu ngân</dt><dd>{receipt.cashierName || '—'}</dd>
                <dt className="text-slate-500">Thời gian thu</dt><dd>{formatDateTime(payment.paidAt)}</dd>
            </dl>
        </article>
    );
}

export default PaymentReceipt;
