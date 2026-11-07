import { useEffect, useId, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import {
    Activity, ArrowRightLeft, CalendarDays, ClipboardPlus, FileCheck2, HeartPulse, Info as InfoIcon,
    LoaderCircle, Pill, Save, Send, Stethoscope, UserRound,
} from 'lucide-react';
import { toast } from 'sonner';
import ParaclinicalOrderPanel from '../../components/paraclinical/ParaclinicalOrderPanel';
import { createdMessage } from '../../components/paraclinical/paraclinicalOrder';
import MedicalHistoryPanel, { AllergyLabels } from '../../components/clinical/MedicalHistoryPanel';
import { TransferRoomPanel } from '../../components/clinical/TransferRoomPanel';
import { ConfirmModal } from '../../components/ui/confirm-modal';
import { VISIT_STATUS_LABELS } from '../../constants/visitData';
import { ROUTES } from '../../routes/routes';
import visitApi from '../../apis/visitApi';
import {
    MSG, ORGAN_FIELDS, VITAL_RULES, examinationErrorMessage, fieldErrorsFromApi, notesKey,
    validateExamination,
} from './clinicalExaminationRules';

const EMPTY_FORM = {
    reason: '', progression: '', generalExam: '', preliminaryDiagnosis: '', icd10: '', treatment: '',
    pulse: '', temperature: '', systolic: '', diastolic: '', respiratoryRate: '',
    weight: '', height: '', spo2: '',
    ...Object.fromEntries(ORGAN_FIELDS.flatMap(([key]) => [[key, ''], [notesKey(key), '']])),
};

function optionalNumber(value) {
    return value === '' || value === null || value === undefined ? null : Number(value);
}

function formFromResponse(data) {
    const findings = Object.fromEntries(ORGAN_FIELDS.flatMap(([key]) => [[key, ''], [notesKey(key), '']]));
    findings.organExam = data?.organFinding ?? '';
    return {
        ...EMPTY_FORM,
        reason: data?.reasonForVisit ?? '', progression: data?.presentIllness ?? '',
        generalExam: data?.generalFinding ?? '', preliminaryDiagnosis: data?.preliminaryDiagnosis ?? '',
        icd10: data?.preliminaryDiagnosisIcd10?.code ?? '', treatment: data?.treatmentCarePerformed ?? '',
        pulse: data?.vitalSigns?.pulse ?? '', temperature: data?.vitalSigns?.temperature ?? '',
        systolic: data?.vitalSigns?.systolicBloodPressure ?? '',
        diastolic: data?.vitalSigns?.diastolicBloodPressure ?? '',
        respiratoryRate: data?.vitalSigns?.respiratoryRate ?? '', weight: data?.vitalSigns?.weight ?? '',
        height: data?.vitalSigns?.height ?? '', spo2: data?.vitalSigns?.spo2 ?? '', ...findings,
    };
}

/** Compares values as the inputs show them, so a loaded number and its typed text are equal. */
function sameForm(left, right) {
    return Object.keys(EMPTY_FORM).every((key) => String(left[key] ?? '') === String(right[key] ?? ''));
}

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

function FieldError({ id, children }) {
    if (!children) return null;
    return <span id={id} className="mt-1 block text-[11px] font-normal text-rose-600">{children}</span>;
}

function Field({ label, value, onChange, placeholder, maxLength = 500, rows = 2, error }) {
    const errorId = useId();
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
                    aria-invalid={error ? 'true' : undefined}
                    aria-describedby={error ? errorId : undefined}
                    className={`w-full resize-none rounded-lg border bg-white px-3 py-2 pr-14 text-xs font-normal leading-5 text-slate-700 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100 ${error ? 'border-rose-400' : 'border-slate-200'}`}
                />
                <span aria-hidden="true" className="absolute bottom-2 right-2 text-[10px] font-normal text-slate-400">{value.length}/{maxLength}</span>
            </span>
            <FieldError id={errorId}>{error}</FieldError>
        </label>
    );
}

function Vital({ label, value, unit, onChange, error, type = 'number' }) {
    const errorId = useId();
    return (
        <label className="min-w-0 text-[11px] font-semibold text-slate-600">
            {label}
            <span className="mt-1 flex items-center gap-1.5">
                <input type={type} value={value} onChange={(event) => onChange(event.target.value)} aria-invalid={error ? 'true' : undefined} aria-describedby={error ? errorId : undefined} className={`h-9 min-w-0 flex-1 rounded-lg border px-2 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 ${error ? 'border-rose-400' : 'border-slate-200'}`} />
                <span className="whitespace-nowrap text-[10px] font-normal text-slate-400">{unit}</span>
            </span>
            <FieldError id={errorId}>{error}</FieldError>
        </label>
    );
}

function Info({ label, children }) {
    return <div className="min-w-0"><p className="text-[10px] text-slate-400">{label}</p><p className="mt-1 truncate text-xs font-semibold text-slate-700">{children || '—'}</p></div>;
}

/** UC-47 Perform Clinical Examination. */
export default function ClinicalExaminationPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const navigationVisit = location.state?.visit ?? null;
    const navigationQueueInfo = location.state?.queueInfo ?? null;
    const returnTab = location.state?.returnTab;
    const visitId = location.state?.visitId ?? navigationVisit?.id ?? null;
    const initialForm = { ...EMPTY_FORM, reason: navigationVisit?.lydovaovien ?? '' };
    const [form, setForm] = useState(initialForm);
    const [savedForm, setSavedForm] = useState(initialForm);
    const [clinicalData, setClinicalData] = useState(null);
    const [loading, setLoading] = useState(Boolean(visitId));
    const [loadError, setLoadError] = useState('');
    const [saving, setSaving] = useState(false);
    const [pageError, setPageError] = useState('');
    const [fieldErrors, setFieldErrors] = useState({});
    const [validationMode, setValidationMode] = useState(null);
    const [pendingLeave, setPendingLeave] = useState(null);
    const [icdOptions, setIcdOptions] = useState([]);
    const [medicalHistory, setMedicalHistory] = useState(null);
    const [activeSection, setActiveSection] = useState('clinical');
    const set = (key) => (value) => {
        const next = { ...form, [key]: value };
        setForm(next);
        if (validationMode) {
            const errors = validateExamination(next, validationMode);
            setFieldErrors(errors);
            setPageError(Object.keys(errors).length
                ? (errors.preliminaryDiagnosis && Object.keys(errors).length === 1
                    ? MSG.DIAGNOSIS_REQUIRED : MSG.CHECK_FIELDS)
                : '');
            if (!Object.keys(errors).length) setValidationMode(null);
        }
    };
    const dirty = !sameForm(form, savedForm);
    const bmi = useMemo(() => {
        const weight = Number(form.weight);
        const heightM = Number(form.height) / 100;
        if (!(weight > 0 && heightM > 0)) return { value: '—', category: '' };
        const value = weight / (heightM * heightM);
        let category = 'Béo phì';
        if (value < 18.5) category = 'Người gầy (thiếu cân)';
        else if (value < 25) category = 'Người bình thường';
        else if (value < 30) category = 'Thừa cân';
        return { value: value.toFixed(1), category };
    }, [form.height, form.weight]);

    const goToWaitingList = () => navigate(ROUTES.DOCTOR.WAITING_LIST, {
        state: returnTab ? { activeTab: returnTab } : undefined,
    });
    // AF-06: MSG38 before leaving with unsaved changes; saved data stays on the server either way.
    const returnToWaitingList = () => {
        if (dirty) setPendingLeave(() => goToWaitingList);
        else goToWaitingList();
    };

    useEffect(() => {
        if (!dirty) return undefined;
        const warn = (event) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    useEffect(() => {
        if (!visitId) return undefined;
        const controller = new AbortController();
        visitApi.openClinicalExamination(visitId, controller.signal)
            .then((data) => {
                const loaded = formFromResponse(data);
                setClinicalData(data);
                setForm(loaded);
                setSavedForm(loaded);
                setLoadError('');
            })
            .catch((error) => {
                // EF-06 / EF-07 / EF-09: editing is not offered when the record cannot be opened.
                if (error?.name !== 'AbortError') setLoadError(examinationErrorMessage(error));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [visitId]);

    useEffect(() => {
        const keyword = form.icd10.trim();
        if (keyword.length < 2) {
            return undefined;
        }
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            visitApi.searchIcd10(keyword, controller.signal)
                .then(setIcdOptions)
                .catch((error) => {
                    if (error?.name !== 'AbortError') setIcdOptions([]);
                });
        }, 300);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [form.icd10]);

    const visit = clinicalData ? {
        ...navigationVisit,
        id: clinicalData.visitId, sovaovien: clinicalData.visitCode, queueNumber: clinicalData.queueNumber,
        status: clinicalData.visitStatus, thoigianvaovien: clinicalData.registeredAt,
        denkhambenhluc: clinicalData.examinationStartedAt, doituongbnLoai: clinicalData.visitType,
        mabhyt: clinicalData.insuranceCardNumber, patientName: clinicalData.patientName,
        patientCode: clinicalData.patientCode, patientDob: clinicalData.patientDateOfBirth,
        patientGender: clinicalData.patientGender, lydovaovien: clinicalData.reasonForVisit,
        occupation: clinicalData.occupation, ethnicity: clinicalData.ethnicity, address: clinicalData.address,
    } : navigationVisit;
    const queueInfo = clinicalData ? {
        roomId: clinicalData.roomId, roomName: clinicalData.roomName, doctorName: clinicalData.doctorName,
    } : navigationQueueInfo;
    const statusLabel = VISIT_STATUS_LABELS[visit?.status] ?? '—';
    const visitTypeLabel = visit?.doituongbnLoai === 'BHYT' ? 'Khám BHYT' : 'Khám dịch vụ';

    const payload = (mode, nextStep = null) => ({
        mode, nextStep, reasonForVisit: form.reason, presentIllness: form.progression,
        generalFinding: form.generalExam,
        vitalSigns: {
            pulse: optionalNumber(form.pulse), temperature: optionalNumber(form.temperature),
            systolicBloodPressure: optionalNumber(form.systolic),
            diastolicBloodPressure: optionalNumber(form.diastolic),
            respiratoryRate: optionalNumber(form.respiratoryRate), weight: optionalNumber(form.weight),
            height: optionalNumber(form.height), spo2: optionalNumber(form.spo2),
            measuredAt: clinicalData?.vitalSigns?.measuredAt ?? new Date().toISOString(),
        },
        organFinding: form.organExam,
        preliminaryDiagnosis: form.preliminaryDiagnosis,
        preliminaryDiagnosisIcd10Code: form.icd10.trim() || null,
        treatmentCarePerformed: form.treatment, version: clinicalData?.version ?? null,
    });

    const save = async (mode, nextStep = null) => {
        const errors = validateExamination(form, mode);
        setFieldErrors(errors);
        if (Object.keys(errors).length) {
            setValidationMode(mode);
            setPageError(errors.preliminaryDiagnosis && Object.keys(errors).length === 1
                ? MSG.DIAGNOSIS_REQUIRED : MSG.CHECK_FIELDS);
            return;
        }
        setValidationMode(null);
        setSaving(true);
        setPageError('');
        try {
            const saved = await visitApi.saveClinicalExamination(visitId, payload(mode, nextStep));
            const savedValues = formFromResponse(saved);
            setClinicalData(saved);
            setForm(savedValues);
            setSavedForm(savedValues);
            toast.success('Cập nhật hồ sơ khám lâm sàng thành công.'); // MSG03
            if (nextStep === 'PARACLINICAL') setActiveSection('paraclinical');
            if (nextStep === 'CONCLUSION') toast.info('Hồ sơ đã sẵn sàng để thực hiện kết luận khám (UC-56).');
        } catch (error) {
            setFieldErrors(fieldErrorsFromApi(error));
            setPageError(examinationErrorMessage(error));
        } finally {
            setSaving(false);
        }
    };

    const saveDraft = () => save('DRAFT');

    const openTransfer = () => {
        setPageError('');
        setFieldErrors({});
        setValidationMode(null);
        setActiveSection('transfer');
    };

    if (loading) {
        return <div role="status" className="flex min-h-64 items-center justify-center gap-3 text-sm text-slate-600"><LoaderCircle aria-hidden="true" className="h-5 w-5 animate-spin" />Đang mở hồ sơ khám lâm sàng…</div>;
    }

    if (loadError) {
        return (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-8 text-center">
                <Stethoscope className="mx-auto h-10 w-10 text-rose-600" />
                <h1 className="mt-3 text-lg font-bold text-slate-800">Không thể mở hồ sơ khám</h1>
                <p role="alert" className="mt-1 text-sm text-rose-800">{loadError}</p>
                <button type="button" onClick={goToWaitingList} className="mt-4 rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white">Về danh sách chờ</button>
            </div>
        );
    }

    if (!visit) {
        return (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-8 text-center">
                <Stethoscope className="mx-auto h-10 w-10 text-amber-600" />
                <h1 className="mt-3 text-lg font-bold text-slate-800">Chưa chọn lượt khám</h1>
                <p className="mt-1 text-sm text-slate-600">Vui lòng bắt đầu một bệnh nhân từ danh sách chờ khám.</p>
                <button type="button" onClick={goToWaitingList} className="mt-4 rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white">Về danh sách chờ</button>
            </div>
        );
    }

    const transferable = clinicalData?.version === 0 && !loadError;
    const tabs = [
        [Stethoscope, 'Khám lâm sàng', 'clinical', () => setActiveSection('clinical')],
        // AF-04: the examination is saved (diagnosis required) before the order screen opens.
        [ClipboardPlus, 'Chỉ định cận lâm sàng', 'paraclinical', () => { if (activeSection !== 'paraclinical') save('CONTINUE', 'PARACLINICAL'); }],
        // UC-48: only before the examination is saved the first time (OQ-05, Q-33).
        [ArrowRightLeft, 'Chuyển phòng', 'transfer', transferable ? openTransfer : null],
        [FileCheck2, 'Chẩn đoán', 'diagnosis', null],
        [Pill, 'Đơn thuốc', 'prescription', null],
        [CalendarDays, 'Kết luận & Hẹn tái khám', 'conclusion', null],
    ];

    return (
        <main className="mx-auto max-w-[1600px] space-y-3 pb-8 text-slate-800">
            <header className="flex items-center justify-between">
                <div className="flex items-center gap-3"><Stethoscope className="h-7 w-7 text-teal-600" /><div><h1 className="text-2xl font-extrabold">Khám lâm sàng</h1><p className="flex items-center text-xs text-slate-400"><button type="button" onClick={returnToWaitingList} className="cursor-pointer font-semibold text-teal-700 hover:text-teal-900 hover:underline">Khám bệnh</button><span className="px-1">›</span><span>Khám lâm sàng</span></p></div></div>
                <button type="button" onClick={saveDraft} disabled={saving} className="inline-flex h-11 items-center gap-2 rounded-lg border border-teal-600 px-4 text-xs font-semibold text-teal-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50">{saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}{saving ? 'Đang lưu…' : 'Lưu bản nháp'}</button>
            </header>

            {pageError && <div role="alert" tabIndex={-1} className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-800">{pageError}</div>}

            <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="grid gap-px bg-slate-100 md:grid-cols-3 xl:grid-cols-6">
                    <div className="flex items-center gap-3 bg-white p-4 xl:col-span-2"><div className="flex h-11 w-11 items-center justify-center rounded-full bg-teal-50 text-teal-700"><UserRound className="h-6 w-6" /></div><div><p className="font-bold">{visit.patientName}</p><p className="text-xs text-slate-500">Mã BN: <strong>{visit.patientCode}</strong></p></div></div>
                    <div className="bg-white p-4"><Info label="Ngày sinh / Tuổi">{formatDate(visit.patientDob)} ({ageOf(visit.patientDob)} tuổi)</Info></div>
                    <div className="bg-white p-4"><Info label="Giới tính">{visit.patientGender}</Info></div>
                    <div className="bg-white p-4"><Info label="Loại khám">{visitTypeLabel}</Info></div>
                    <div className="bg-white p-4"><Info label="Số thẻ BHYT">{visit.mabhyt}</Info></div>
                </div>
                <div className="grid gap-px border-t border-slate-100 bg-slate-100 sm:grid-cols-2 lg:grid-cols-4">
                    <div className="bg-white px-4 py-3"><Info label="Nghề nghiệp">{visit.occupation}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Dân tộc">{visit.ethnicity}</Info></div>
                    <div className="bg-white px-4 py-3 lg:col-span-2"><Info label="Địa chỉ">{visit.address}</Info></div>
                </div>
                <div className="grid gap-px border-t border-slate-100 bg-slate-100 sm:grid-cols-3 lg:grid-cols-6">
                    <div className="bg-white px-4 py-3"><Info label="Mã lượt khám">{visit.sovaovien}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Số thứ tự">{visit.queueNumber}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Phòng khám">{queueInfo?.roomName}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Bác sĩ khám">{queueInfo?.doctorName}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Giờ bắt đầu khám">{formatTime(visit.denkhambenhluc)}</Info></div>
                    <div className="bg-white px-4 py-3"><Info label="Trạng thái lượt khám">{statusLabel}</Info></div>
                </div>
                <div className="flex flex-wrap items-center gap-2 border-t border-slate-100 px-4 py-2.5 text-xs"><span className="font-semibold text-slate-500">Dị ứng thuốc:</span><AllergyLabels data={medicalHistory} /></div>
            </section>

            <nav className="flex gap-2 overflow-x-auto" aria-label="Các bước khám bệnh">
                {tabs.map(([Icon, label, id, action]) => <button key={label} type="button" onClick={action ?? undefined} disabled={!action || (id === 'paraclinical' && saving)} className={`inline-flex min-w-40 items-center justify-center gap-2 rounded-full border px-4 py-2.5 text-xs font-semibold ${activeSection === id ? 'border-teal-700 bg-teal-700 text-white' : 'border-slate-200 bg-white text-slate-500 disabled:opacity-60'}`}><Icon className="h-4 w-4" />{label}</button>)}
            </nav>

            {activeSection === 'transfer' ? (
                <TransferRoomPanel visit={visit} currentRoomName={queueInfo?.roomName} onCancel={() => setActiveSection('clinical')} onDone={goToWaitingList} />
            ) : activeSection === 'paraclinical' ? (
                // UC-49: after the order the visit is Waiting for Paraclinical, so the examination closes (MSG67).
                <ParaclinicalOrderPanel visit={visit} preliminaryDiagnosis={form.preliminaryDiagnosis} onCancel={() => setActiveSection('clinical')} onCreated={(order) => { toast.success(createdMessage(order)); goToWaitingList(); }} />
            ) : <><div className="grid items-start gap-3 xl:grid-cols-[minmax(0,1fr)_260px]">
                <div className="space-y-3">
                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="mb-2 text-sm font-bold">II. Lý do vào viện</h2><Field label="Lý do khám / triệu chứng chính *" value={form.reason} onChange={set('reason')} placeholder="Nhập lý do khám, triệu chứng chính..." maxLength={1000} error={fieldErrors.reason} /></section>

                    <MedicalHistoryPanel visitId={visit.id} onLoaded={setMedicalHistory} />

                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="mb-3 text-sm font-bold">III. Hỏi bệnh</h2><Field label="Quá trình bệnh lý" value={form.progression} onChange={set('progression')} placeholder="Diễn tiến triệu chứng và tình trạng hiện tại..." maxLength={4000} rows={4} error={fieldErrors.progression} /></section>

                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                        <h2 className="mb-3 text-sm font-bold">IV. Khám bệnh</h2>
                        <div className="rounded-xl border border-teal-100 bg-teal-50/40 p-3"><div className="mb-3 flex items-center gap-2 text-xs font-bold text-teal-800"><HeartPulse className="h-4 w-4" />Sinh hiệu <span className="font-normal text-slate-400">• Thời điểm đo: {formatTime(clinicalData?.vitalSigns?.measuredAt ?? new Date())}</span></div><div className="grid grid-cols-2 gap-3 sm:grid-cols-4">{Object.entries(VITAL_RULES).map(([key, [label, unit]]) => <Vital key={key} label={label} value={form[key]} onChange={set(key)} unit={unit} error={fieldErrors[key]} />)}</div>{fieldErrors.vitals && <p className="mt-2 text-[11px] text-rose-600">{fieldErrors.vitals}</p>}<div className="mt-3 flex items-center gap-2 text-xs text-slate-500"><Activity className="h-4 w-4 text-teal-600" />BMI: <strong className="text-slate-700">{bmi.value}</strong> kg/m²{bmi.category && <strong className="text-teal-700">({bmi.category})</strong>}</div></div>
                        <div className="mt-4"><Field label="1. Toàn thân" value={form.generalExam} onChange={set('generalExam')} placeholder="Mô tả tình trạng toàn thân..." maxLength={4000} rows={3} error={fieldErrors.generalExam} /></div>
                        <div className="mt-4"><Field label="2. Khám bộ phận" value={form.organExam} onChange={set('organExam')} placeholder="Nhập kết quả khám bộ phận..." maxLength={2000} rows={4} error={fieldErrors.organExam} /></div>
                        <div className="mt-4 grid gap-3 md:grid-cols-2"><div className="grid grid-cols-[minmax(0,1fr)_180px] gap-3"><Field label="4. Chẩn đoán ban đầu" value={form.preliminaryDiagnosis} onChange={set('preliminaryDiagnosis')} placeholder="Bắt buộc khi chuyển bước..." maxLength={1000} error={fieldErrors.preliminaryDiagnosis} /><label className="block text-xs font-semibold text-slate-700">Mã ICD-10 (không bắt buộc)<input list="icd10-options" value={form.icd10} onChange={(event) => set('icd10')(event.target.value)} placeholder="Tìm mã hoặc tên bệnh" aria-invalid={fieldErrors.icd10 ? 'true' : undefined} className={`mt-1 h-11 w-full rounded-lg border bg-white px-3 text-xs font-normal outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 ${fieldErrors.icd10 ? 'border-rose-400' : 'border-slate-200'}`} /><datalist id="icd10-options">{form.icd10.trim().length >= 2 && icdOptions.map((option) => <option key={option.code} value={option.code}>{option.name}</option>)}</datalist>{fieldErrors.icd10 ? <FieldError>{fieldErrors.icd10}</FieldError> : <span className="mt-1 block text-[10px] font-normal leading-4 text-slate-500">Chọn một gợi ý để lưu đúng mã đang hoạt động.</span>}</label></div><Field label="5. Điều trị / chăm sóc đã thực hiện" value={form.treatment} onChange={set('treatment')} placeholder="Nhập thông tin đã xử lý, chăm sóc, hướng dẫn..." maxLength={4000} error={fieldErrors.treatment} /></div>
                    </section>
                </div>

                <aside className="sticky top-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="flex items-center gap-2 text-sm font-bold"><CalendarDays className="h-4 w-4 text-teal-600" />Thông tin lượt khám</h2><dl className="mt-4 space-y-3 text-xs">{[['Mã lượt khám', visit.sovaovien], ['Ngày khám', formatDate(visit.thoigianvaovien)], ['Giờ đăng ký', formatTime(visit.thoigianvaovien)], ['Giờ bắt đầu khám', formatTime(visit.denkhambenhluc)], ['Số thứ tự', visit.queueNumber], ['Loại khám', visitTypeLabel], ['Phòng khám', queueInfo?.roomName], ['Bác sĩ khám', queueInfo?.doctorName]].map(([label, value]) => <div key={label} className="grid grid-cols-[88px_1fr] gap-2"><dt className="text-slate-400">{label}</dt><dd className="font-semibold text-slate-700">{value || '—'}</dd></div>)}</dl><div className="mt-4 inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700"><span className="h-2 w-2 rounded-full bg-emerald-500" />{statusLabel}</div></aside>
            </div>

            <div className="flex flex-wrap justify-end gap-2"><button type="button" onClick={returnToWaitingList} disabled={saving} className="min-h-11 rounded-lg border border-slate-300 px-4 py-2.5 text-xs font-semibold text-slate-600 disabled:opacity-50">Quay lại danh sách</button><button type="button" onClick={saveDraft} disabled={saving} className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-teal-700 px-5 py-2.5 text-xs font-semibold text-teal-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50"><Save aria-hidden="true" className="h-4 w-4" />Lưu bản nháp</button><button type="button" onClick={() => save('CONTINUE', 'CONCLUSION')} disabled={saving} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-5 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50">{saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <FileCheck2 aria-hidden="true" className="h-4 w-4" />}Lưu và chuyển kết luận</button><button type="button" onClick={() => save('CONTINUE', 'PARACLINICAL')} disabled={saving} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-sky-700 px-5 py-2.5 text-xs font-semibold text-white hover:bg-sky-800 disabled:cursor-not-allowed disabled:opacity-50"><ClipboardPlus aria-hidden="true" className="h-4 w-4" />Lưu và chỉ định CLS</button></div>
            </>}

            <ConfirmModal
                isOpen={Boolean(pendingLeave)}
                title="Rời khỏi trang khám?"
                description={MSG.UNSAVED_CHANGES}
                confirmText="Rời đi"
                cancelText="Ở lại"
                variant="destructive"
                onConfirm={() => { const leave = pendingLeave; setPendingLeave(null); leave?.(); }}
                onClose={() => setPendingLeave(null)}
            />
        </main>
    );
}
