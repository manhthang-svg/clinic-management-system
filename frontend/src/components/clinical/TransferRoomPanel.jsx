import { useCallback, useEffect, useId, useState } from 'react';
import { Info as InfoIcon, LoaderCircle, Send } from 'lucide-react';
import { toast } from 'sonner';
import { ConfirmModal } from '../ui/confirm-modal';
import { VisitTicket } from '../visits';
import visitApi from '../../apis/visitApi';
import { GENDER_LABELS } from '../../constants/accountData';
import { VISIT_STATUS_LABELS } from '../../constants/visitData';
import { NOTES_MAX, REASON_MAX, TRANSFER_MESSAGES, transferError, validateTransfer } from './transferRoom';

const inputClass = 'mt-1 w-full rounded-lg border bg-white px-3 text-xs font-normal outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';

function formatDob(value) {
    if (!value) return '';
    const [year, month, day] = String(value).slice(0, 10).split('-');
    return day && month && year ? `${day}/${month}/${year}` : String(value);
}

function visitTypeText(visit) {
    if (visit.doituongbnLoai !== 'BHYT') return 'Khám dịch vụ';
    return visit.mabhyt ? `Khám BHYT (${visit.mabhyt})` : 'Khám BHYT';
}

/**
 * UC-48 Transfer Patient to Another Room, from the examination of a called-in patient whose examination has not
 * been saved yet (OQ-05, Q-33). After the transfer the Doctor can print the ticket of the new room (AF-02).
 */
export function TransferRoomPanel({ visit, currentRoomName, onCancel, onDone, onVisitChanged, api = visitApi }) {
    const roomFieldId = useId();
    const reasonId = useId();
    const notesId = useId();
    const [rooms, setRooms] = useState([]);
    const [roomsState, setRoomsState] = useState('loading');
    const [toRoomId, setToRoomId] = useState('');
    const [reason, setReason] = useState('');
    const [notes, setNotes] = useState('');
    const [errors, setErrors] = useState({});
    const [pageError, setPageError] = useState('');
    const [ended, setEnded] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [confirmCancel, setConfirmCancel] = useState(false);
    const [result, setResult] = useState(null);
    const [ticket, setTicket] = useState({ data: null, loading: false, error: null });

    // The first load starts in 'loading'; a reload sets it through reloadRooms.
    const loadRooms = useCallback((signal) => {
        return api.getTransferRooms(visit.id, signal)
            .then((items) => {
                setRooms(Array.isArray(items) ? items : []);
                setRoomsState('ready');
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;
                const failure = transferError(error);
                if (failure.ends) {
                    setPageError(failure.message);
                    setEnded(true);
                }
                setRoomsState('error');
            });
    }, [api, visit.id]);

    useEffect(() => {
        const controller = new AbortController();
        void loadRooms(controller.signal);
        return () => controller.abort();
    }, [loadRooms]);

    const reloadRooms = () => {
        setRoomsState('loading');
        void loadRooms();
    };

    const target = rooms.find((room) => room.roomId === toRoomId);
    const dirty = Boolean(toRoomId || reason || notes);
    const noRoom = roomsState === 'ready' && rooms.length === 0;
    const canSubmit = !ended && !submitting && !noRoom && roomsState === 'ready' && toRoomId && reason.trim();

    const cancel = () => {
        if (dirty) setConfirmCancel(true);
        else onCancel();
    };

    const submit = async (event) => {
        event.preventDefault();
        const found = validateTransfer({ toRoomId, reason, notes });
        setErrors(found);
        if (Object.keys(found).length > 0) return;
        setSubmitting(true);
        setPageError('');
        try {
            const transferred = await api.transferVisit(visit.id, {
                toRoomId, reason: reason.trim(), notes: notes.trim() || null,
            });
            toast.success(TRANSFER_MESSAGES.transferred(transferred.toRoomName, transferred.newQueueNumber));
            setResult(transferred);
        } catch (error) {
            const failure = transferError(error, target?.roomName);
            if (failure.field) setErrors({ [failure.field]: failure.message });
            else setPageError(failure.message);
            if (failure.ends) setEnded(true);
            if (failure.reloadVisit) {
                // EF-04 (MSG75): the page reloads the visit; the toast stays if the transfer tab closes.
                toast.error(failure.message);
                onVisitChanged?.();
            }
            if (failure.reloadRooms) {
                setToRoomId('');
                reloadRooms();
            }
        } finally {
            setSubmitting(false);
        }
    };

    const openTicket = async () => {
        setTicket({ data: null, loading: true, error: null });
        try {
            setTicket({ data: await api.getTicket(visit.id), loading: false, error: null });
        } catch {
            setTicket({ data: null, loading: false, error: TRANSFER_MESSAGES.systemError });
        }
    };

    const printTicket = async () => {
        setTicket((current) => ({ ...current, loading: true, error: null }));
        try {
            const printed = await api.prepareTicketPrint(visit.id);
            setTicket({ data: printed, loading: false, error: null });
            window.setTimeout(() => window.print(), 0);
        } catch {
            // The transfer stays; the Doctor can retry (MSG80).
            setTicket((current) => ({ ...current, loading: false, error: TRANSFER_MESSAGES.systemError }));
        }
    };

    if (result && (ticket.data || ticket.loading || ticket.error)) {
        return (
            <VisitTicket
                title="In phiếu khám"
                visit={{ id: visit.id, sovaovien: visit.sovaovien, status: 'WAITING' }}
                ticket={ticket.data}
                loading={ticket.loading}
                error={ticket.error}
                onReload={openTicket}
                onPrint={printTicket}
                onDone={onDone}
            />
        );
    }

    if (result) {
        return (
            <section className="rounded-xl border border-emerald-200 bg-white p-5 shadow-sm">
                <h2 className="text-base font-bold text-emerald-800">Chuyển phòng thành công</h2>
                <p role="status" className="mt-2 text-sm text-slate-700">{TRANSFER_MESSAGES.transferred(result.toRoomName, result.newQueueNumber)}</p>
                <dl className="mt-4 grid gap-2 text-xs sm:grid-cols-3">
                    <div><dt className="text-slate-400">Phòng mới</dt><dd className="font-semibold">{result.toRoomCode} - {result.toRoomName}</dd></div>
                    <div><dt className="text-slate-400">Vị trí</dt><dd className="font-semibold">{result.toRoomLocation || '—'}</dd></div>
                    <div><dt className="text-slate-400">Số thứ tự mới</dt><dd className="font-semibold">{result.newQueueNumber}</dd></div>
                </dl>
                <div className="mt-5 flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
                    <button type="button" onClick={onDone} className="min-h-11 rounded-lg border border-slate-300 px-5 py-2.5 text-xs font-semibold text-slate-600">Về danh sách chờ</button>
                    <button type="button" onClick={openTicket} className="min-h-11 rounded-lg bg-teal-700 px-5 py-2.5 text-xs font-semibold text-white hover:bg-teal-800">In phiếu khám</button>
                </div>
            </section>
        );
    }

    return (
        <form noValidate onSubmit={submit} className="grid items-start gap-3 xl:grid-cols-[minmax(0,1fr)_320px]">
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <h2 className="text-base font-bold">Chuyển phòng khám</h2>
                {pageError && <p role="alert" className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-medium text-rose-800">{pageError}</p>}
                {noRoom && <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">{TRANSFER_MESSAGES.noEligibleRoom}</p>}
                {roomsState === 'error' && !ended && (
                    <p className="mt-3 flex flex-wrap items-center gap-2 text-xs text-rose-700">{TRANSFER_MESSAGES.loadRoomsError}<button type="button" onClick={reloadRooms} className="font-semibold underline">Thử lại</button></p>
                )}

                <div className="mt-4 grid gap-4 md:grid-cols-2">
                    <label htmlFor={roomFieldId} className="text-xs font-semibold text-slate-700">
                        Phòng chuyển đến <span className="text-rose-500">*</span>
                        <select
                            id={roomFieldId}
                            value={toRoomId}
                            onChange={(event) => { setToRoomId(event.target.value); setErrors((current) => ({ ...current, toRoomId: undefined })); }}
                            disabled={roomsState !== 'ready' || noRoom || ended}
                            aria-invalid={errors.toRoomId ? 'true' : undefined}
                            className={`${inputClass} h-11 ${errors.toRoomId ? 'border-rose-400' : 'border-slate-200'}`}
                        >
                            <option value="">{roomsState === 'loading' ? 'Đang tải phòng khám…' : 'Chọn phòng khám'}</option>
                            {rooms.map((room) => <option key={room.roomId} value={room.roomId}>{room.roomCode} - {room.roomName}</option>)}
                        </select>
                        {errors.toRoomId && <span className="mt-1 block text-[11px] font-normal text-rose-600">{errors.toRoomId}</span>}
                    </label>
                    <dl className="grid grid-cols-[88px_1fr] gap-x-2 gap-y-1 self-end rounded-lg bg-slate-50 p-3 text-xs">
                        <dt className="text-slate-400">Khoa</dt><dd className="font-semibold">{target?.departmentName || '—'}</dd>
                        <dt className="text-slate-400">Vị trí</dt><dd className="font-semibold">{target?.location || '—'}</dd>
                        <dt className="text-slate-400">Bác sĩ trực</dt><dd className="font-semibold">{target?.doctorName || '—'}</dd>
                    </dl>
                </div>

                <label htmlFor={reasonId} className="mt-4 block text-xs font-semibold text-slate-700">
                    Lý do chuyển <span className="text-rose-500">*</span>
                    <textarea
                        id={reasonId}
                        rows={3}
                        maxLength={REASON_MAX}
                        value={reason}
                        onChange={(event) => { setReason(event.target.value); setErrors((current) => ({ ...current, reason: undefined })); }}
                        disabled={ended}
                        placeholder="Nhập lý do chuyển phòng (5–500 ký tự)…"
                        aria-invalid={errors.reason ? 'true' : undefined}
                        className={`${inputClass} py-2 ${errors.reason ? 'border-rose-400' : 'border-slate-200'}`}
                    />
                    <span className="mt-1 flex justify-between text-[11px] font-normal">
                        <span className="text-rose-600">{errors.reason}</span>
                        <span className="text-slate-400">{reason.trim().length}/{REASON_MAX}</span>
                    </span>
                </label>

                <label htmlFor={notesId} className="mt-3 block text-xs font-semibold text-slate-700">
                    Ghi chú (không bắt buộc)
                    <textarea
                        id={notesId}
                        rows={2}
                        maxLength={NOTES_MAX}
                        value={notes}
                        onChange={(event) => setNotes(event.target.value)}
                        disabled={ended}
                        placeholder="Ghi chú cho phòng nhận…"
                        className={`${inputClass} py-2 border-slate-200`}
                    />
                    {errors.notes && <span className="mt-1 block text-[11px] font-normal text-rose-600">{errors.notes}</span>}
                </label>

                <div className="mt-4 rounded-xl border border-sky-200 bg-sky-50 p-4 text-xs leading-6 text-sky-900">
                    <p className="flex items-center gap-2 font-bold"><InfoIcon aria-hidden="true" className="h-4 w-4 text-sky-600" />Sau khi xác nhận chuyển phòng</p>
                    <ul className="ml-6 list-disc">
                        <li>Lượt khám trở về “Chờ khám” ở phòng mới với số thứ tự mới, xếp sau bệnh nhân ưu tiên và trước bệnh nhân thường.</li>
                        <li>Không thu thêm phí khám; hóa đơn phí khám giữ nguyên.</li>
                        <li>Hồ sơ khám chưa có nội dung, bác sĩ phòng mới khám tiếp trên cùng lượt khám.</li>
                    </ul>
                </div>

                <div className="mt-5 flex justify-between border-t border-slate-100 pt-4">
                    <button type="button" onClick={cancel} disabled={submitting} className="min-h-11 rounded-lg border border-slate-300 px-5 py-2.5 text-xs font-semibold text-slate-600 disabled:opacity-50">Hủy</button>
                    <button type="submit" disabled={!canSubmit} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50">
                        {submitting ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Send aria-hidden="true" className="h-4 w-4" />}
                        {submitting ? 'Đang chuyển…' : 'Xác nhận chuyển'}
                    </button>
                </div>
            </section>

            <aside className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <h2 className="text-sm font-bold">Lượt khám hiện tại</h2>
                <dl className="mt-3 space-y-2 text-xs">
                    {[
                        ['Bệnh nhân', visit.patientName],
                        ['Mã bệnh nhân', visit.patientCode],
                        ['Ngày sinh', formatDob(visit.patientDob)],
                        ['Giới tính', GENDER_LABELS[visit.patientGender] ?? visit.patientGender],
                        ['Mã lượt khám', visit.sovaovien],
                        ['Loại khám', visitTypeText(visit)],
                        ['Phòng hiện tại', currentRoomName],
                        ['Số thứ tự hiện tại', visit.queueNumber],
                        ['Lý do khám', visit.lydovaovien],
                        ['Trạng thái', VISIT_STATUS_LABELS[visit.status] ?? visit.status],
                    ].map(([label, value]) => (
                        <div key={label} className="flex justify-between gap-3"><dt className="text-slate-400">{label}</dt><dd className="text-right font-semibold">{value || '—'}</dd></div>
                    ))}
                </dl>
            </aside>

            <ConfirmModal
                isOpen={confirmCancel}
                title="Hủy chuyển phòng?"
                description={TRANSFER_MESSAGES.unsavedChanges}
                confirmText="Hủy chuyển phòng"
                cancelText="Ở lại"
                variant="destructive"
                onConfirm={() => { setConfirmCancel(false); onCancel(); }}
                onClose={() => setConfirmCancel(false)}
            />
        </form>
    );
}

export default TransferRoomPanel;
