import { useId, useState } from 'react';
import { CheckCircle2, LoaderCircle } from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import { ConfirmModal } from '../ui/confirm-modal';
import { CLOSE_MSG, RETENTION_CATEGORIES, closeError, closureProblems } from './closeRecord';

/**
 * UC-58 Close Medical Record: "Xác nhận kết luận" checks what blocks (BR-58-01), asks MSG27 with the
 * "Loại hồ sơ lưu trữ" (BR-58-06), then closes the record and completes the visit in one action (BR-32).
 */
export function ConfirmConclusionSection({ visitId, saved, dirty, api = defaultVisitApi, onClosed, onOpenPrescription }) {
    const categoryId = useId();
    const [checking, setChecking] = useState(false);
    const [problems, setProblems] = useState([]);
    const [check, setCheck] = useState(null);
    const [category, setCategory] = useState('STANDARD');
    const [dialogError, setDialogError] = useState('');

    const start = async () => {
        setProblems([]);
        setChecking(true);
        try {
            const result = await api.getConclusionClosure(visitId);
            const found = closureProblems(result);
            if (found.length) {
                setProblems(found);
            } else {
                setCategory('STANDARD');
                setDialogError('');
                setCheck(result);
            }
        } catch (error) {
            setProblems([closeError(error)]);
        } finally {
            setChecking(false);
        }
    };

    const confirm = async () => {
        setDialogError('');
        try {
            const result = await api.confirmConclusion(visitId, {
                conclusionVersion: check?.conclusionVersion ?? null,
                retentionCategory: category,
            });
            setCheck(null);
            toast.success(CLOSE_MSG.closed(result?.visitCode ?? '')); // MSG130
            onClosed?.(result);
        } catch (error) {
            const problem = closeError(error);
            if (problem.key === 'category') {
                setDialogError(problem.message);
            } else {
                setCheck(null);
                setProblems([problem]);
            }
        }
    };

    const disabled = !saved || dirty || checking;
    return (
        <section aria-label="Xác nhận kết luận" className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                    <h2 className="text-sm font-bold">Xác nhận kết luận và đóng hồ sơ</h2>
                    <p className="mt-1 text-xs text-slate-500">
                        {dirty ? CLOSE_MSG.SAVE_FIRST : !saved ? CLOSE_MSG.CONCLUSION_REQUIRED
                            : 'Sau khi xác nhận, hồ sơ bệnh án được khóa và lưu trữ.'}
                    </p>
                </div>
                <button
                    type="button"
                    onClick={() => { void start(); }}
                    disabled={disabled}
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-teal-700 bg-white px-6 py-2.5 text-xs font-semibold text-teal-800 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {checking ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <CheckCircle2 aria-hidden="true" className="h-4 w-4" />}
                    {checking ? 'Đang kiểm tra…' : 'Xác nhận kết luận'}
                </button>
            </div>

            {problems.length > 0 && (
                <div role="alert" className="space-y-1 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-medium text-rose-800">
                    {problems.map((problem) => (
                        <p key={problem.key}>
                            {problem.message}
                            {problem.key === 'prescription' && onOpenPrescription && (
                                <> <button type="button" onClick={onOpenPrescription} className="font-semibold underline">Mở tab Kê đơn</button></>
                            )}
                        </p>
                    ))}
                </div>
            )}

            <ConfirmModal
                isOpen={Boolean(check)}
                title="Xác nhận kết luận"
                description={CLOSE_MSG.CONFIRM}
                confirmText="Xác nhận"
                cancelText="Hủy"
                onConfirm={confirm}
                onClose={() => setCheck(null)}
            >
                <label htmlFor={categoryId} className="block text-xs font-semibold text-slate-700">Loại hồ sơ lưu trữ</label>
                <select
                    id={categoryId}
                    value={category}
                    onChange={(event) => setCategory(event.target.value)}
                    className="mt-1 h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-xs text-slate-700 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                >
                    {RETENTION_CATEGORIES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
                {dialogError && <p role="alert" className="mt-2 text-xs text-rose-700">{dialogError}</p>}
            </ConfirmModal>
        </section>
    );
}

export default ConfirmConclusionSection;
