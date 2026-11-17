import { useCallback, useEffect, useState } from 'react';
import { generatePath, useNavigate, useParams } from 'react-router';
import { AlertTriangle, CalendarClock, CalendarPlus, Pencil, Printer, RefreshCw } from 'lucide-react';
import defaultPatientApi from '../../apis/patientApi';
import defaultVisitApi from '../../apis/visitApi';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { VisitTicket } from '../../components/visits';
import { needsPaymentGuide } from '../../components/visits/visitFormat';
import { clinicToday } from '../../components/visits/visitForm';
import { loadErrorMessage, msg22 } from '../../components/visits/visitErrors';
import { VISIT_STATUS_LABELS, VISIT_TYPE_LABELS } from '../../constants/visitData';
import {
    GENDER_LABELS,
    MSG,
    PAYMENT_STATUS_LABELS,
    ageOn,
    canRegisterVisit,
    formatDate,
    formatDateTime,
    fullAddress,
    overviewErrorMessage,
} from '../../components/patients/patientOverview';
import { ROUTES } from '../../routes/routes';

function Info({ label, children }) {
    return (
        <div>
            <dt className="text-xs font-semibold text-slate-500">{label}</dt>
            <dd className="mt-0.5 text-sm text-slate-800">{children || '—'}</dd>
        </div>
    );
}

// UC-36: trang xem ho so benh nhan (sua ho so, dang ky kham, in lai phieu kham)
export default function ReceptionistPatientDetailPage({ patientApi = defaultPatientApi, visitApi = defaultVisitApi }) {
    const { id } = useParams();
    const navigate = useNavigate();
    const [state, setState] = useState({ loading: true, error: '', data: null });
    const [appointment, setAppointment] = useState(null);
    const [reprint, setReprint] = useState(null);

    const goToPatients = useCallback(() => navigate(ROUTES.RECEPTIONIST.PATIENTS), [navigate]);

    // tai ho so + lich hen tai kham
    const load = useCallback(async (signal) => {
        setState((current) => ({ ...current, loading: true, error: '' }));
        try {
            const data = await patientApi.overview(id, signal);
            setState({ loading: false, error: '', data });
        } catch (error) {
            if (error?.name !== 'AbortError') setState({ loading: false, error: overviewErrorMessage(error), data: null });
        }
        try {
            // loi tai lich hen thi van hien ho so
            setAppointment((await visitApi.getFollowUpAppointment(id, signal)) ?? null);
        } catch {
            setAppointment(null);
        }
    }, [id, patientApi, visitApi]);

    useEffect(() => {
        const controller = new AbortController();
        const timer = window.setTimeout(() => void load(controller.signal), 0);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [load]);

    // in lai phieu kham cua luot kham dang mo
    const openReprint = useCallback(async (activeVisit) => {
        const visit = { id: activeVisit.visitId, sovaovien: activeVisit.registrationCode, status: activeVisit.status };
        setReprint({ visit, ticket: null, loading: true, error: null });
        try {
            const ticket = await visitApi.getTicket(visit.id);
            setReprint((current) => current && { ...current, ticket, loading: false });
        } catch (error) {
            setReprint((current) => current && { ...current, loading: false, error: loadErrorMessage(error) });
        }
    }, [visitApi]);

    const printTicket = async () => {
        if (!reprint?.ticket) return;
        // phieu huong dan thanh toan in luon, khong can goi server
        if (needsPaymentGuide(reprint.ticket)) {
            window.print();
            return;
        }
        setReprint((current) => ({ ...current, loading: true, error: null }));
        try {
            const ticket = await visitApi.prepareTicketPrint(reprint.visit.id);
            setReprint((current) => ({ ...current, ticket, loading: false }));
            window.setTimeout(() => window.print(), 0);
        } catch (error) {
            setReprint((current) => ({ ...current, loading: false, error: loadErrorMessage(error) }));
        }
    };

    const header = (
        <AccountPageHeader
            title="Thông tin bệnh nhân"
            description="Thông tin hành chính và lịch sử khám của bệnh nhân"
            crumbs={[{ label: 'Quản lý bệnh nhân', onClick: goToPatients }, { label: 'Thông tin bệnh nhân' }]}
        />
    );

    if (reprint) {
        return (
            <div className="mx-auto w-full max-w-[1200px] space-y-6">
                {header}
                <VisitTicket
                    title="In lại phiếu khám"
                    visit={reprint.visit}
                    ticket={reprint.ticket}
                    loading={reprint.loading}
                    error={reprint.error}
                    onReload={() => openReprint({
                        visitId: reprint.visit.id, registrationCode: reprint.visit.sovaovien, status: reprint.visit.status,
                    })}
                    onPrint={printTicket}
                    onDone={() => setReprint(null)}
                />
            </div>
        );
    }

    if (state.loading && !state.data) {
        return (
            <div className="mx-auto w-full max-w-[1200px] space-y-6">
                {header}
                <p role="status" className="text-sm text-slate-500">Đang tải hồ sơ bệnh nhân…</p>
            </div>
        );
    }

    if (state.error) {
        return (
            <div className="mx-auto w-full max-w-[1200px] space-y-6">
                {header}
                <section className="rounded-2xl border border-slate-200 bg-white p-10 text-center shadow-2xs">
                    <AlertTriangle className="mx-auto h-8 w-8 text-amber-600" />
                    <p role="alert" className="mt-3 text-sm text-slate-600">{state.error}</p>
                    <button
                        type="button"
                        onClick={goToPatients}
                        className="mt-5 rounded-xl bg-[#127e7a] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[#0e6b67]"
                    >
                        Về danh sách bệnh nhân
                    </button>
                </section>
            </div>
        );
    }

    const { patient, drugAllergies, lastInsuranceCard, activeVisit, visits } = state.data;
    const today = clinicToday();
    const age = ageOn(patient.ngaysinh, today);
    const registerAllowed = canRegisterVisit(activeVisit);
    const appointmentToday = appointment?.appointmentDate === today ? appointment : null;

    return (
        <div className="mx-auto w-full max-w-[1200px] space-y-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
                {header}
                <div className="flex flex-wrap gap-2">
                    <button
                        type="button"
                        onClick={() => navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_EDIT, { id: patient.id }))}
                        className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                    >
                        <Pencil size={16} />
                        Cập nhật thông tin
                    </button>
                    <button
                        type="button"
                        disabled={!registerAllowed}
                        title={registerAllowed || !activeVisit ? undefined : msg22({ sovaovien: activeVisit.registrationCode })}
                        onClick={() => navigate(generatePath(ROUTES.RECEPTIONIST.VISIT_REGISTER, { id: patient.id }))}
                        className="inline-flex items-center gap-2 rounded-xl bg-[#0F9D9A] px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        <CalendarPlus size={16} />
                        Đăng ký khám
                    </button>
                </div>
            </div>

            {drugAllergies.length > 0 && (
                <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    Dị ứng thuốc: {drugAllergies.join(', ')}
                </p>
            )}

            {activeVisit && (
                <section aria-label="Lượt khám đang mở" className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                            <h2 className="text-sm! font-bold! text-amber-900">Lượt khám đang mở</h2>
                            <p className="mt-1 text-sm text-amber-900">
                                <span className="font-mono">{activeVisit.registrationCode}</span>
                                {' · '}{VISIT_STATUS_LABELS[activeVisit.status] ?? activeVisit.status}
                                {' · '}{activeVisit.roomName ?? '—'}
                                {' · '}Phí khám: {PAYMENT_STATUS_LABELS[activeVisit.examinationFeeStatus] ?? 'Chưa có phiếu thu'}
                            </p>
                        </div>
                        <button
                            type="button"
                            onClick={() => void openReprint(activeVisit)}
                            className="inline-flex items-center gap-2 rounded-xl border border-amber-300 bg-white px-4 py-2 text-sm font-semibold text-amber-800 hover:bg-amber-100"
                        >
                            <Printer size={16} />
                            In lại phiếu khám
                        </button>
                    </div>
                </section>
            )}

            {appointmentToday && (
                <section aria-label="Lịch tái khám hôm nay" className="flex items-center gap-3 rounded-2xl border border-teal-200 bg-teal-50 p-5 text-sm text-teal-900">
                    <CalendarClock size={20} className="shrink-0" />
                    <span>
                        Có lịch tái khám hôm nay ({formatDate(appointmentToday.appointmentDate)})
                        {appointmentToday.doctorName ? ` với ${appointmentToday.doctorName}` : ''}
                        {appointmentToday.suggestedRoomName ? `, phòng ${appointmentToday.suggestedRoomName}` : ''}.
                    </span>
                </section>
            )}

            <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                <h2 className="mb-4 text-base! font-bold! text-slate-800">Thông tin hành chính</h2>
                <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    <Info label="Mã bệnh nhân"><span className="font-mono">{patient.mabenhnhan}</span></Info>
                    <Info label="Họ và tên">{patient.hoten}</Info>
                    <Info label="Ngày sinh">{formatDate(patient.ngaysinh)}{age !== null ? ` (${age} tuổi)` : ''}</Info>
                    <Info label="Giới tính">{GENDER_LABELS[patient.gioitinh]}</Info>
                    <Info label="CCCD / Hộ chiếu">{patient.cccdSo || patient.hochieuSo}</Info>
                    <Info label="Số điện thoại">{patient.sodienthoai}</Info>
                    <Info label="Địa chỉ">{fullAddress(patient)}</Info>
                    <Info label="Nghề nghiệp">{patient.nghenghiep}</Info>
                    <Info label="Dân tộc">{patient.dantoc}</Info>
                    <Info label="Quốc tịch">{patient.ngoaikieu || 'Việt Nam'}</Info>
                    <Info label="Thẻ BHYT gần nhất"><span className="font-mono">{lastInsuranceCard}</span></Info>
                    <Info label="Người giám hộ">
                        {patient.hotennguoithan
                            ? [patient.hotennguoithan, patient.nguoithanQuanhe, patient.sodienthoainguoithan].filter(Boolean).join(' · ')
                            : null}
                    </Info>
                </dl>
            </section>

            <section className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-100 p-5">
                    <h2 className="text-base! font-bold! text-slate-800">Lịch sử khám</h2>
                    <button
                        type="button"
                        onClick={() => void load()}
                        disabled={state.loading}
                        className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                    >
                        <RefreshCw size={14} className={state.loading ? 'animate-spin' : ''} />
                        Tải lại
                    </button>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full min-w-[800px] text-left">
                        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                            <tr>
                                {['Mã lượt khám', 'Thời gian', 'Phòng', 'Bác sĩ', 'Đối tượng', 'Trạng thái'].map((heading) => (
                                    <th key={heading} className="whitespace-nowrap px-5 py-3 font-semibold">{heading}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 text-sm text-slate-700">
                            {visits.map((visit) => (
                                <tr key={visit.visitId}>
                                    <td className="px-5 py-3 font-mono text-teal-700">{visit.registrationCode}</td>
                                    <td className="px-5 py-3">{formatDateTime(visit.registeredAt)}</td>
                                    <td className="px-5 py-3">{visit.roomName ?? '—'}</td>
                                    <td className="px-5 py-3">{visit.doctorName ?? '—'}</td>
                                    <td className="px-5 py-3">{VISIT_TYPE_LABELS[visit.visitType] ?? visit.visitType}</td>
                                    <td className="px-5 py-3">{VISIT_STATUS_LABELS[visit.status] ?? visit.status}</td>
                                </tr>
                            ))}
                            {visits.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="px-5 py-10 text-center text-slate-500">{MSG.NO_HISTORY}</td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </section>
        </div>
    );
}
