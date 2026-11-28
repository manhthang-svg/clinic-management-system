import { useId, useState } from 'react';
import { LoaderCircle, Link2, Unlink } from 'lucide-react';

import { accountErrorMessage } from './accountErrors';

// BR-59-17 (ASSUMPTION wording, MSG numbers TBD).
const NATIONAL_PRESCRIBER_MSG = {
    ADDED: (time) => `Đã thêm vào cơ sở trên Hệ thống đơn thuốc quốc gia lúc ${time}.`,
    NOT_ADDED: 'Chưa thêm vào cơ sở trên Hệ thống đơn thuốc quốc gia. Bác sĩ chưa gửi được đơn thuốc.',
    NO_LINK_CODE: 'Cần nhập mã liên thông bác sĩ trước khi thêm vào cơ sở.',
    PASSWORD_HINT: 'Mật khẩu liên thông của cơ sở do Hệ thống đơn thuốc quốc gia cấp. Mật khẩu chỉ dùng để đăng nhập, hệ thống không lưu lại.',
    SIGN_IN_REQUIRED: 'Vui lòng nhập mật khẩu liên thông của cơ sở.',
    SIGN_IN_FAILED: (reason) => `Hệ thống đơn thuốc quốc gia từ chối đăng nhập${reason ? `: ${reason}` : '.'}`,
    REJECTED: (reason) => `Hệ thống đơn thuốc quốc gia từ chối yêu cầu${reason ? `: ${reason}` : '.'}`,
    NO_ANSWER: 'Không thể kết nối với Hệ thống đơn thuốc quốc gia. Vui lòng thử lại.', // MSG86
    FACILITY_LINK_CODE: 'Cơ sở chưa có mã liên thông đơn thuốc. Vui lòng cập nhật thông tin cơ sở.',
};

function formatDateTime(value) {
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

function errorMessage(error) {
    const code = error?.errorResponse?.code;
    const reason = error?.errorResponse?.details?.errors;
    switch (code) {
        case 'NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED':
            return NATIONAL_PRESCRIBER_MSG.SIGN_IN_REQUIRED;
        case 'NATIONAL_PRESCRIPTION_SIGN_IN_FAILED':
            return NATIONAL_PRESCRIBER_MSG.SIGN_IN_FAILED(reason);
        case 'NATIONAL_PRESCRIPTION_REQUEST_REJECTED':
            return NATIONAL_PRESCRIBER_MSG.REJECTED(reason);
        case 'NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE':
            return NATIONAL_PRESCRIBER_MSG.NO_ANSWER;
        case 'PRESCRIBER_LINK_CODE_REQUIRED':
            return NATIONAL_PRESCRIBER_MSG.NO_LINK_CODE;
        case 'FACILITY_LINK_CODE_REQUIRED':
            return NATIONAL_PRESCRIBER_MSG.FACILITY_LINK_CODE;
        default:
            return accountErrorMessage(error);
    }
}

/**
 * UC-93 (BR-59-17, QĐ 808 IV, V): the Admin adds the Doctor to the facility on the national prescription system, or
 * removes them. The facility's password is asked only when the server has no facility token and is not kept.
 */
export function NationalPrescriberPanel({ account, accountApi, onUpdated }) {
    const [passwordNeeded, setPasswordNeeded] = useState(false);
    const [password, setPassword] = useState('');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const passwordId = useId();
    const added = Boolean(account.lienthongAddedAt);
    const hasLinkCode = Boolean(account.maLienthongBacsi);

    const run = async () => {
        setBusy(true);
        setMessage('');
        try {
            const fresh = await accountApi.detail(account.id);
            const body = { version: fresh.version, ...(passwordNeeded ? { facilityPassword: password } : {}) };
            const updated = added
                ? await accountApi.removeFromNationalPrescriptionSystem(account.id, body)
                : await accountApi.addToNationalPrescriptionSystem(account.id, body);
            setPasswordNeeded(false);
            onUpdated(updated);
        } catch (error) {
            if (error?.errorResponse?.code === 'NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED') setPasswordNeeded(true);
            setMessage(errorMessage(error));
        } finally {
            setPassword('');
            setBusy(false);
        }
    };

    const incomplete = passwordNeeded && password.trim() === '';
    const label = added ? 'Xóa khỏi cơ sở' : 'Thêm vào cơ sở';
    return (
        <section aria-label="Liên thông đơn thuốc" className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-3">
            <h2 className="text-base! font-bold! text-[#1b2533]">Liên thông đơn thuốc</h2>
            <p role="status" className={`text-sm ${added ? 'text-emerald-700' : 'text-amber-700'}`}>
                {added ? NATIONAL_PRESCRIBER_MSG.ADDED(formatDateTime(account.lienthongAddedAt)) : NATIONAL_PRESCRIBER_MSG.NOT_ADDED}
            </p>
            {!hasLinkCode && !added && <p className="text-sm text-slate-500">{NATIONAL_PRESCRIBER_MSG.NO_LINK_CODE}</p>}
            <form
                noValidate
                onSubmit={(event) => { event.preventDefault(); if (!incomplete && !busy) void run(); }}
                className="space-y-3"
            >
                {passwordNeeded && (
                    <div className="max-w-sm">
                        <label htmlFor={passwordId} className="block text-sm font-semibold text-slate-700">Mật khẩu liên thông của cơ sở</label>
                        <input
                            id={passwordId}
                            type="password"
                            autoComplete="off"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-[#e6f5f3]"
                        />
                        <p className="mt-1 text-xs text-slate-500">{NATIONAL_PRESCRIBER_MSG.PASSWORD_HINT}</p>
                    </div>
                )}
                {message && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{message}</p>}
                <button
                    type="submit"
                    disabled={busy || incomplete || (!added && !hasLinkCode)}
                    className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${added ? 'border-rose-200 text-rose-600 hover:bg-rose-50' : 'border-[#127e7a] text-[#127e7a] hover:bg-[#e6f5f3]'}`}
                >
                    {busy ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />
                        : added ? <Unlink aria-hidden="true" className="h-4 w-4" /> : <Link2 aria-hidden="true" className="h-4 w-4" />}
                    {busy ? 'Đang gửi…' : label}
                </button>
            </form>
        </section>
    );
}

export default NationalPrescriberPanel;
