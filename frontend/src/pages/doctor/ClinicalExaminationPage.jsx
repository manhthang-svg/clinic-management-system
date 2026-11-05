import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import {
    Activity, ArrowRightLeft, CalendarDays, ClipboardPlus, FileCheck2, HeartPulse, Info as InfoIcon,
    Pill, Save, Send, Stethoscope, UserRound,
} from 'lucide-react';
import { toast } from 'sonner';
import ParaclinicalOrderDialog from '../../components/paraclinical/ParaclinicalOrderDialog';
import MedicalHistoryPanel, { AllergyLabels } from '../../components/clinical/MedicalHistoryPanel';
import { ROUTES } from '../../routes/routes';
import visitApi from '../../apis/visitApi';

const ORGAN_FIELDS = [
    ['circulatory', 'Tuần hoàn'], ['respiratory', 'Hô hấp'], ['digestive', 'Tiêu hóa'],
    ['ent', 'Tai mũi họng'], ['urinary', 'Thận - tiết niệu'], ['neurological', 'Thần kinh'],
    ['musculoskeletal', 'Cơ xương khớp'], ['other', 'Nội tiết / Khác'],
];

const EMPTY_FORM = {
    reason: '', progression: '', personalHistory: '', familyHistory: '', allergies: '',
    generalExam: '', paraclinicalSummary: '', preliminaryDiagnosis: '', icd10: '', treatment: '',
    pulse: '', temperature: '', systolic: '', diastolic: '', respiratoryRate: '',
    weight: '', height: '', spo2: '', ...Object.fromEntries(ORGAN_FIELDS.map(([key]) => [key, ''])),
};

function ageOf(dob) {
    if (!dob) return '—';
    const birth = new Date(`${dob}T00:00:00`);
    if (Number.isNaN(birth.getTime())) return '—';
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    if (today < new Date(today.getFullYear(), birth.getMonth(), birth.getDate())) age -= 1;
    return age;
}

function formatDate(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short' }).format(new Date(value));
}

function formatTime(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

function Field({ label, value, onChange, placeholder, maxLength = 500, rows = 2 }) {
    return (
        <label className="block min-w-0 text-xs font-semibold text-slate-700">
            <span>{label}</span>
            <span className="relative mt-1 block">
                <textarea
                    rows={rows}
                    maxLength={maxLength}
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                    placeholder={placeholder}
                    className="w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 pr-14 text-xs font-normal leading-5 text-slate-700 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                />
                <span className="absolute bottom-2 right-2 text-[10px] font-normal text-slate-400">{value.length}/{maxLength}</span>
            </span>
        </label>
    );
}

function Vital({ label, value, unit, onChange, type = 'number' }) {
    return (
        <label className="min-w-0 text-[11px] font-semibold text-slate-600">
            {label}
            <span className="mt-1 flex items-center gap-1.5">
                <input type={type} value={value} onChange={(event) => onChange(event.target.value)} className="h-9 min-w-0 flex-1 rounded-lg border border-slate-200 px-2 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100" />
                <span className="whitespace-nowrap text-[10px] font-normal text-slate-400">{unit}</span>
            </span>
        </label>
    );
}

function Info({ label, children }) {
    return <div className="min-w-0"><p className="text-[10px] text-slate-400">{label}</p><p className="mt-1 truncate text-xs font-semibold text-slate-700">{children || '—'}</p></div>;
}

function TransferRoomPanel({ visit, queueInfo, onCancel }) {
    const [rooms, setRooms] = useState([]);
    const [targetRoomId, setTargetRoomId] = useState('');
    const [reason, setReason] = useState('');
    const [note, setNote] = useState('');
    const [loading, setLoading] = useState(true);
    const [includeExam, setIncludeExam] = useState(true);
    const [includeOrders, setIncludeOrders] = useState(true);

    useEffect(() => {
        const controller = new AbortController();
        visitApi.getActiveExaminationRooms(controller.signal)
            .then((items) => setRooms((Array.isArray(items) ? items : []).filter((room) => room.id !== queueInfo?.roomId)))
            .catch((error) => {
                if (error?.name !== 'AbortError') {
                    toast.error('Không thể tải danh sách phòng khám đang hoạt động.');
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [queueInfo?.roomId]);

    const target = rooms.find((room) => room.id === targetRoomId);
    const submit = () => {
        if (!targetRoomId || !reason.trim()) {
            toast.error('Vui lòng chọn phòng chuyển đến và nhập lý do chuyển.');
            return;
        }
        localStorage.setItem(`room-transfer:${visit.id}`, JSON.stringify({
            visitId: visit.id, targetRoomId, reason: reason.trim(), note: note.trim(), includeExam, includeOrders,
        }));
        toast.info('Đã lưu yêu cầu chuyển phòng tạm thời. Backend UC48 chưa hỗ trợ xác nhận chuyển phòng.');
    };

    return (
        <div className="grid items-start gap-3 xl:grid-cols-[minmax(0,1fr)_320px]">
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <h2 className="text-base font-bold">1. Thông tin chuyển phòng</h2>
                <div className="mt-4 grid gap-4 md:grid-cols-2">
                    <label className="text-xs font-semibold text-slate-700">Phòng khám chuyển đến <span className="text-rose-500">*</span><select value={targetRoomId} onChange={(event) => setTargetRoomId(event.target.value)} disabled={loading} className="mt-1 h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"><option value="">{loading ? 'Đang tải phòng khám...' : 'Chọn phòng khám'}</option>{rooms.map((room) => <option key={room.id} value={room.id}>{room.code} - {room.name}</option>)}</select></label>
                    <label className="text-xs font-semibold text-slate-700">Bác sĩ phụ trách (nếu biết)<input readOnly value={target ? 'Bác sĩ trực tại phòng' : ''} placeholder="Chọn phòng để xem bác sĩ" className="mt-1 h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs text-slate-500" /></label>
                </div>
                <div className="mt-4"><Field label="Lý do chuyển *" value={reason} onChange={setReason} placeholder="Nhập lý do chuyển phòng..." maxLength={500} rows={3} /></div>

                <h2 className="mb-3 mt-6 text-base font-bold">2. Thông tin bổ sung</h2>
                <div className="grid gap-3 text-xs text-slate-600 md:grid-cols-2">
                    <label className="flex items-center gap-2"><input type="checkbox" checked={includeExam} onChange={(event) => setIncludeExam(event.target.checked)} className="h-4 w-4 accent-teal-600" />Chuyển kèm kết quả khám đã ghi nhận</label>
                    <label className="flex items-center gap-2"><input type="checkbox" checked={includeOrders} onChange={(event) => setIncludeOrders(event.target.checked)} className="h-4 w-4 accent-teal-600" />Chuyển kèm chỉ định cận lâm sàng</label>
                    <label className="flex items-center gap-2"><input type="checkbox" className="h-4 w-4 accent-teal-600" />Chuyển kèm đơn thuốc hiện tại</label>
                    <label className="flex items-center gap-2"><input type="checkbox" className="h-4 w-4 accent-teal-600" />Ghi chú cho bác sĩ nhận</label>
                </div>
                <div className="mt-4"><Field label="Ghi chú (nếu có)" value={note} onChange={setNote} placeholder="Nhập ghi chú..." maxLength={200} rows={3} /></div>

                <h2 className="mb-3 mt-6 text-base font-bold">3. Hướng xử lý sau khi chuyển</h2>
                <div className="rounded-xl border border-sky-200 bg-sky-50 p-4 text-xs leading-6 text-sky-900"><p className="flex items-center gap-2 font-bold"><InfoIcon className="h-4 w-4 text-sky-600" />Sau khi xác nhận chuyển phòng</p><ul className="ml-6 list-disc"><li>Bệnh nhân sẽ được đưa vào danh sách chờ khám của phòng được chọn.</li><li>Trạng thái lượt khám trở về “Chờ khám” và nhận số thứ tự mới.</li><li>Thông tin khám hiện tại được giữ lại cho bác sĩ phòng nhận.</li></ul></div>

                <div className="mt-5 flex justify-between border-t border-slate-100 pt-4"><button type="button" onClick={onCancel} className="rounded-lg border border-slate-300 px-5 py-2.5 text-xs font-semibold text-slate-600">Hủy</button><button type="button" onClick={submit} className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800"><Send className="h-4 w-4" />Xác nhận chuyển phòng</button></div>
            </section>

            <aside className="space-y-3"><section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="text-sm font-bold">Tóm tắt quá trình khám</h2><div className="mt-4 space-y-4 border-l-2 border-teal-200 pl-4 text-xs"><div><strong>Đăng ký khám</strong><p className="text-slate-400">{formatTime(visit.thoigianvaovien)} · {queueInfo?.roomName}</p></div><div><strong>Khám lâm sàng</strong><p className="text-slate-400">{queueInfo?.doctorName}</p></div><div className="text-sky-700"><strong>Chuyển phòng khám</strong><p>{target ? `${target.code} - ${target.name}` : 'Chưa chọn phòng'}</p></div></div></section><section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="text-sm font-bold">Thông tin khám đã ghi nhận</h2><dl className="mt-3 space-y-2 text-xs"><div className="flex justify-between gap-3"><dt className="text-slate-400">Lý do khám</dt><dd className="text-right font-semibold">{visit.lydovaovien || '—'}</dd></div><div className="flex justify-between gap-3"><dt className="text-slate-400">Phòng hiện tại</dt><dd className="text-right font-semibold">{queueInfo?.roomName || '—'}</dd></div><div className="flex justify-between gap-3"><dt className="text-slate-400">Bác sĩ</dt><dd className="text-right font-semibold">{queueInfo?.doctorName || '—'}</dd></div></dl></section></aside>
        </div>
    );
}

export default function ClinicalExaminationPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const visit = location.state?.visit ?? null;
    const queueInfo = location.state?.queueInfo ?? null;
    const returnTab = location.state?.returnTab;
    const returnToWaitingList = () => navigate(ROUTES.DOCTOR.WAITING_LIST, {
        state: returnTab ? { activeTab: returnTab } : undefined,
    });
    const draftKey = `clinical-examination:${location.state?.visitId ?? visit?.id ?? 'unknown'}`;
    const [form, setForm] = useState(() => {
        try {
            const draft = localStorage.getItem(draftKey);
            return draft ? { ...EMPTY_FORM, ...JSON.parse(draft) } : { ...EMPTY_FORM, reason: visit?.lydovaovien ?? '' };
        } catch {
            return { ...EMPTY_FORM, reason: visit?.lydovaovien ?? '' };
        }
    });
    const [orderOpen, setOrderOpen] = useState(false);
    const [medicalHistory, setMedicalHistory] = useState(null);
    const [activeSection, setActiveSection] = useState('clinical');
    const set = (key) => (value) => setForm((current) => ({ ...current, [key]: value }));
    const bmi = useMemo(() => {
        const weight = Number(form.weight);
        const heightM = Number(form.height) / 100;
        return weight > 0 && heightM > 0 ? (weight / (heightM * heightM)).toFixed(1) : '—';
    }, [form.height, form.weight]);

    const saveDraft = () => {
        localStorage.setItem(draftKey, JSON.stringify(form));
        toast.success('Đã lưu bản nháp khám lâm sàng trên trình duyệt.');
    };

    if (!visit) {
        return (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-8 text-center">
                <Stethoscope className="mx-auto h-10 w-10 text-amber-600" />
                <h1 className="mt-3 text-lg font-bold text-slate-800">Chưa chọn lượt khám</h1>
                <p className="mt-1 text-sm text-slate-600">Vui lòng bắt đầu một bệnh nhân từ danh sách chờ khám.</p>
                <button type="button" onClick={returnToWaitingList} className="mt-4 rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white">Về danh sách chờ</button>
            </div>
        );
    }

    const tabs = [
        [Stethoscope, 'Khám lâm sàng', 'clinical', () => setActiveSection('clinical')],
        [ClipboardPlus, 'Chỉ định cận lâm sàng', 'paraclinical', () => setOrderOpen(true)],
        [ArrowRightLeft, 'Chuyển phòng', 'transfer', () => setActiveSection('transfer')],
        [FileCheck2, 'Chẩn đoán', 'diagnosis', null],
        [Pill, 'Đơn thuốc', 'prescription', null],
        [CalendarDays, 'Kết luận & Hẹn tái khám', 'conclusion', null],
    ];

    return (
        <main className="mx-auto max-w-[1600px] space-y-3 pb-8 text-slate-800">
            <header className="flex items-center justify-between">
                <div className="flex items-center gap-3"><Stethoscope className="h-7 w-7 text-teal-600" /><div><h1 className="text-2xl font-extrabold">Khám lâm sàng</h1><p className="flex items-center text-xs text-slate-400"><button type="button" onClick={returnToWaitingList} className="cursor-pointer font-semibold text-teal-700 hover:text-teal-900 hover:underline">Khám bệnh</button><span className="px-1">›</span><span>Khám lâm sàng</span></p></div></div>
                <button type="button" onClick={saveDraft} className="inline-flex h-10 items-center gap-2 rounded-lg border border-teal-600 px-4 text-xs font-semibold text-teal-700 hover:bg-teal-50"><Save className="h-4 w-4" />Lưu bản nháp</button>
            </header>

            <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="grid gap-px bg-slate-100 md:grid-cols-3 xl:grid-cols-6">
                    <div className="flex items-center gap-3 bg-white p-4 xl:col-span-2"><div className="flex h-11 w-11 items-center justify-center rounded-full bg-teal-50 text-teal-700"><UserRound className="h-6 w-6" /></div><div><p className="font-bold">{visit.patientName}</p><p className="text-xs text-slate-500">Mã BN: <strong>{visit.patientCode}</strong></p></div></div>
                    <div className="bg-white p-4"><Info label="Ngày sinh / Tuổi">{formatDate(visit.patientDob)} ({ageOf(visit.patientDob)} tuổi)</Info></div>
                    <div className="bg-white p-4"><Info label="Giới tính">{visit.patientGender}</Info></div>
                    <div className="bg-white p-4"><Info label="Loại khám">{visit.doituongbnLoai === 'BHYT' ? 'Khám BHYT' : 'Khám dịch vụ'}</Info></div>
                    <div className="bg-white p-4"><Info label="Số thẻ BHYT">{visit.mabhyt}</Info></div>
                </div>
                <div className="grid gap-px border-t border-slate-100 bg-slate-100 sm:grid-cols-2 lg:grid-cols-4">
                    <div className="bg-white px-4 py-3"><Info label="Số lượt khám">{visit.queueNumber}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Phòng khám">{queueInfo?.roomName}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Bác sĩ khám">{queueInfo?.doctorName}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Giờ bắt đầu">{formatTime(new Date())}</Info></div>
                </div>
                <div className="flex flex-wrap items-center gap-2 border-t border-slate-100 px-4 py-2.5 text-xs"><span className="font-semibold text-slate-500">Dị ứng thuốc:</span><AllergyLabels data={medicalHistory} /></div>
            </section>

            <nav className="flex gap-2 overflow-x-auto" aria-label="Các bước khám bệnh">
                {tabs.map(([Icon, label, id, action]) => <button key={label} type="button" onClick={action ?? undefined} disabled={!action} className={`inline-flex min-w-40 items-center justify-center gap-2 rounded-full border px-4 py-2.5 text-xs font-semibold ${activeSection === id ? 'border-teal-700 bg-teal-700 text-white' : 'border-slate-200 bg-white text-slate-500 disabled:opacity-60'}`}><Icon className="h-4 w-4" />{label}</button>)}
            </nav>

            {activeSection === 'transfer' ? (
                <TransferRoomPanel visit={visit} queueInfo={queueInfo} onCancel={() => setActiveSection('clinical')} />
            ) : <><div className="grid items-start gap-3 xl:grid-cols-[minmax(0,1fr)_260px]">
                <div className="space-y-3">
                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="mb-2 text-sm font-bold">II. Lý do vào viện</h2><Field label="" value={form.reason} onChange={set('reason')} placeholder="Nhập lý do khám, triệu chứng chính..." /></section>

                    <MedicalHistoryPanel visitId={visit.id} onLoaded={setMedicalHistory} />

                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="mb-3 text-sm font-bold">III. Hỏi bệnh</h2><div className="grid gap-3 md:grid-cols-2"><Field label="Quá trình bệnh lý" value={form.progression} onChange={set('progression')} placeholder="Nhập quá trình bệnh lý..." maxLength={1000} /><Field label="Tiền sử bản thân" value={form.personalHistory} onChange={set('personalHistory')} placeholder="Nhập tiền sử bản thân..." /><Field label="Tiền sử gia đình" value={form.familyHistory} onChange={set('familyHistory')} placeholder="Nhập tiền sử gia đình..." /><Field label="Dị ứng (thuốc, thức ăn, khác)" value={form.allergies} onChange={set('allergies')} placeholder="Nhập thông tin dị ứng (nếu có)..." /></div></section>

                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                        <h2 className="mb-3 text-sm font-bold">IV. Khám bệnh</h2>
                        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(520px,1.2fr)]"><Field label="1. Toàn thân" value={form.generalExam} onChange={set('generalExam')} placeholder="Mô tả tình trạng toàn thân..." maxLength={1000} rows={3} /><div className="rounded-xl border border-teal-100 bg-teal-50/40 p-3"><div className="mb-3 flex items-center gap-2 text-xs font-bold text-teal-800"><HeartPulse className="h-4 w-4" />Sinh hiệu <span className="font-normal text-slate-400">• Thời điểm đo: {formatTime(new Date())}</span></div><div className="grid grid-cols-2 gap-3 sm:grid-cols-4"><Vital label="Mạch" value={form.pulse} onChange={set('pulse')} unit="lần/phút" /><Vital label="Nhiệt độ" value={form.temperature} onChange={set('temperature')} unit="°C" /><Vital label="Huyết áp tâm thu" value={form.systolic} onChange={set('systolic')} unit="mmHg" /><Vital label="Huyết áp tâm trương" value={form.diastolic} onChange={set('diastolic')} unit="mmHg" /><Vital label="Nhịp thở" value={form.respiratoryRate} onChange={set('respiratoryRate')} unit="lần/phút" /><Vital label="Cân nặng" value={form.weight} onChange={set('weight')} unit="kg" /><Vital label="Chiều cao" value={form.height} onChange={set('height')} unit="cm" /><Vital label="SpO₂" value={form.spo2} onChange={set('spo2')} unit="%" /></div><div className="mt-3 flex items-center gap-2 text-xs text-slate-500"><Activity className="h-4 w-4 text-teal-600" />BMI: <strong className="text-slate-700">{bmi}</strong> kg/m²</div></div></div>
                        <h3 className="mb-2 mt-4 text-xs font-bold">2. Các bộ phận</h3><div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">{ORGAN_FIELDS.map(([key, label]) => <Field key={key} label={label} value={form[key]} onChange={set(key)} placeholder="Nhập kết quả khám..." maxLength={300} />)}</div>
                        <div className="mt-4"><Field label="3. Tóm tắt kết quả cận lâm sàng" value={form.paraclinicalSummary} onChange={set('paraclinicalSummary')} placeholder="Nhập tóm tắt kết quả cận lâm sàng (nếu có)..." maxLength={1000} /></div>
                        <div className="mt-4 grid gap-3 md:grid-cols-2"><div className="grid grid-cols-[minmax(0,1fr)_140px] gap-3"><Field label="4. Chẩn đoán ban đầu" value={form.preliminaryDiagnosis} onChange={set('preliminaryDiagnosis')} placeholder="Nhập chẩn đoán..." /><Field label="Mã ICD-10" value={form.icd10} onChange={set('icd10')} placeholder="VD: J18.9" maxLength={10} /></div><Field label="5. Đã xử lý (thuốc, chăm sóc)" value={form.treatment} onChange={set('treatment')} placeholder="Nhập thông tin đã xử lý, chăm sóc, hướng dẫn..." /></div>
                    </section>
                </div>

                <aside className="sticky top-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="flex items-center gap-2 text-sm font-bold"><CalendarDays className="h-4 w-4 text-teal-600" />Thông tin lượt khám</h2><dl className="mt-4 space-y-3 text-xs">{[['Ngày khám', formatDate(visit.thoigianvaovien)], ['Giờ đăng ký', formatTime(visit.thoigianvaovien)], ['Số thứ tự', visit.queueNumber], ['Loại khám', visit.doituongbnLoai === 'BHYT' ? 'Khám BHYT' : 'Khám dịch vụ'], ['Phòng khám', queueInfo?.roomName], ['Bác sĩ khám', queueInfo?.doctorName]].map(([label, value]) => <div key={label} className="grid grid-cols-[88px_1fr] gap-2"><dt className="text-slate-400">{label}</dt><dd className="font-semibold text-slate-700">{value || '—'}</dd></div>)}</dl><div className="mt-4 inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700"><span className="h-2 w-2 rounded-full bg-emerald-500" />Đang khám</div></aside>
            </div>

            <div className="flex justify-end gap-2"><button type="button" onClick={returnToWaitingList} className="rounded-lg border border-slate-300 px-4 py-2.5 text-xs font-semibold text-slate-600">Quay lại danh sách</button><button type="button" onClick={saveDraft} className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-5 py-2.5 text-xs font-semibold text-white hover:bg-teal-800"><Save className="h-4 w-4" />Lưu bản nháp</button></div>
            </>}

            {orderOpen && <ParaclinicalOrderDialog visit={visit} onClose={() => setOrderOpen(false)} onCreated={() => { setOrderOpen(false); toast.success('Đã tạo chỉ định cận lâm sàng.'); }} />}
        </main>
    );
}
