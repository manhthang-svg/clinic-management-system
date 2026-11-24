import { useEffect, useId, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import {
    Activity, ArrowRightLeft, CalendarDays, ClipboardList, ClipboardPlus, FileCheck2, FolderHeart, HeartPulse, Info as InfoIcon,
    LoaderCircle, Pill, Save, Send, Stethoscope, UserRound, X,
} from 'lucide-react';
import { toast } from 'sonner';
import ParaclinicalOrderPanel from '../../components/paraclinical/ParaclinicalOrderPanel';
import ParaclinicalOrderList from '../../components/paraclinical/ParaclinicalOrderList';
import { cancelledMessage, createdMessage } from '../../components/paraclinical/paraclinicalOrder';
import MedicalHistoryPanel from '../../components/clinical/MedicalHistoryPanel';
import ConclusionPanel from '../../components/clinical/ConclusionPanel';
import FollowUpPanel from '../../components/clinical/FollowUpPanel';
import PrescriptionPanel from '../../components/clinical/PrescriptionPanel';
import { TransferRoomPanel } from '../../components/clinical/TransferRoomPanel';
import { ClosedRecordPrintDialog } from '../../components/clinical/ClosedRecordPrint';
import { ConfirmModal } from '../../components/ui/confirm-modal';
import { REFERRAL_SOURCE_LABELS, VISIT_STATUS_LABELS } from '../../constants/visitData';
import { EXAMINATION_SECTIONS, ROUTES, clinicalExaminationPath } from '../../routes/routes';
import visitApi from '../../apis/visitApi';
import {
    MSG, ORGAN_FIELDS, REFERRAL_HEALTH_FACILITY, VITAL_RULES, examinationErrorMessage, fieldErrorsFromApi, notesKey,
    validateExamination,
} from './clinicalExaminationRules';

const EMPTY_FORM = {
    reason: '', referralSource: '', referralDiagnosis: '', generalExam: '', diagnoses: [],
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
        reason: data?.reasonForVisit ?? '',
        referralSource: data?.referralSource ?? '', referralDiagnosis: data?.referralDiagnosis ?? '',
        generalExam: data?.generalFinding ?? '', diagnoses: data?.preliminaryDiagnoses
            ?? (data?.preliminaryDiagnosisIcd10 ? [data.preliminaryDiagnosisIcd10] : []),
        pulse: data?.vitalSigns?.pulse ?? '', temperature: data?.vitalSigns?.temperature ?? '',
        systolic: data?.vitalSigns?.systolicBloodPressure ?? '',
        diastolic: data?.vitalSigns?.diastolicBloodPressure ?? '',
        respiratoryRate: data?.vitalSigns?.respiratoryRate ?? '', weight: data?.vitalSigns?.weight ?? '',
        height: data?.vitalSigns?.height ?? '', spo2: data?.vitalSigns?.spo2 ?? '', ...findings,
    };
}

/** Compares values as the inputs show them, so a loaded number and its typed text are equal. */
/** The page message for client errors: the diagnosis message when it is the only error, otherwise MSG.CHECK_FIELDS. */
function pageErrorFor(errors) {
    const keys = Object.keys(errors);
    if (!keys.length) return '';
    return keys.length === 1 && errors.preliminaryDiagnosis ? errors.preliminaryDiagnosis : MSG.CHECK_FIELDS;
}

function sameForm(left, right) {
    return Object.keys(EMPTY_FORM).every((key) => (key === 'diagnoses'
        ? JSON.stringify(left[key] ?? []) === JSON.stringify(right[key] ?? [])
        : String(left[key] ?? '') === String(right[key] ?? '')));
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
    return <div className="min-w-0"><p className="text-[10px] leading-4 text-slate-400">{label}</p><p className="truncate text-xs font-semibold leading-5 text-slate-700">{children || '—'}</p></div>;
}

const SERVICES_MAX_AGE_MS = 60_000;

/** Starts loading the paraclinical service list; the order panel shows a failure itself. */
function startServicesRequest() {
    const promise = visitApi.getParaclinicalServices();
    promise.catch(() => {});
    return { promise, at: Date.now() };
}

/** UC-47 Perform Clinical Examination. */
export default function ClinicalExaminationPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const navigationVisit = location.state?.visit ?? null;
    const navigationQueueInfo = location.state?.queueInfo ?? null;
    const returnTab = location.state?.returnTab;
    // The visit and the open tab are in the link (/doctor/clinical-examination/{visitId}/{tab}), so a reload or a
    // shared link opens the same visit and tab. Older links carry the visit only in the navigation state.
    const { visitId: routeVisitId, section: routeSection } = useParams();
    const visitId = routeVisitId ?? location.state?.visitId ?? navigationVisit?.id ?? null;
    const activeSection = EXAMINATION_SECTIONS.includes(routeSection) ? routeSection : 'clinical';
    const setActiveSection = (section, { replace = false } = {}) => {
        if (visitId) navigate(clinicalExaminationPath(visitId, section), { replace, state: location.state });
    };
    const initialForm = { ...EMPTY_FORM, reason: navigationVisit?.lydovaovien ?? '' };
    const [form, setForm] = useState(initialForm);
    const [savedForm, setSavedForm] = useState(initialForm);
    const [clinicalData, setClinicalData] = useState(null);
    const [loading, setLoading] = useState(Boolean(visitId));
    // UC-48 EF-04: bumped to reload the visit details after the visit changed meanwhile.
    const [reloadKey, setReloadKey] = useState(0);
    const [loadError, setLoadError] = useState('');
    const [saving, setSaving] = useState(false);
    const [pageError, setPageError] = useState('');
    const [fieldErrors, setFieldErrors] = useState({});
    const [validationMode, setValidationMode] = useState(null);
    const [pendingLeave, setPendingLeave] = useState(null);
    // UC-58 → UC-57, UC-60, UC-62: the slips are printed once the record is closed (PO 23/11/2026).
    const [closure, setClosure] = useState(null);
    const [icdOptions, setIcdOptions] = useState([]);
    const [icdQuery, setIcdQuery] = useState('');
    // The paraclinical service list starts loading with the page, so the order tab usually opens with it ready.
    const [servicesRequest, setServicesRequest] = useState(() => (visitId ? startServicesRequest() : null));

    // An older link without the visit id moves to the visit's own link.
    useEffect(() => {
        if (!routeVisitId && visitId) {
            navigate(clinicalExaminationPath(visitId), { replace: true, state: location.state });
        }
    }, [location.state, navigate, routeVisitId, visitId]);
    const revalidate = (next, query) => {
        if (!validationMode) return;
        const errors = validateExamination(next, validationMode, query);
        setFieldErrors(errors);
        setPageError(pageErrorFor(errors));
        if (!Object.keys(errors).length) setValidationMode(null);
    };
    const set = (key) => (value) => {
        const next = { ...form, [key]: value };
        setForm(next);
        revalidate(next, icdQuery);
    };
    const changeIcdQuery = (value) => {
        setIcdQuery(value);
        revalidate(form, value);
    };
    const addDiagnosis = (option) => {
        if (!option || form.diagnoses.some((item) => item.code === option.code)) return;
        const next = { ...form, diagnoses: [...form.diagnoses, option] };
        setForm(next);
        setIcdQuery('');
        setIcdOptions([]);
        revalidate(next, '');
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
    }, [visitId, reloadKey]);

    useEffect(() => {
        const keyword = icdQuery.trim();
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
    }, [icdQuery]);

    const visit = clinicalData ? {
        ...navigationVisit,
        id: clinicalData.visitId, patientId: clinicalData.patientId ?? navigationVisit?.patientId, sovaovien: clinicalData.visitCode, queueNumber: clinicalData.queueNumber,
        status: clinicalData.visitStatus, thoigianvaovien: clinicalData.registeredAt,
        denkhambenhluc: clinicalData.examinationStartedAt, doituongbnLoai: clinicalData.visitType,
        mabhyt: clinicalData.insuranceCardNumber, insuranceCoverageRate: clinicalData.insuranceCoverageRate,
        insuranceScopeTotal: clinicalData.insuranceScopeTotal,
        coPaymentExemptionThreshold: clinicalData.coPaymentExemptionThreshold,
        patientName: clinicalData.patientName,
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
        mode, nextStep, reasonForVisit: form.reason,
        referralDiagnosis: form.referralSource === REFERRAL_HEALTH_FACILITY ? form.referralDiagnosis : null,
        presentIllness: clinicalData?.presentIllness ?? null,
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
        preliminaryDiagnosis: form.diagnoses.map((item) => item.name).join('; ') || null,
        preliminaryDiagnosisIcd10Code: form.diagnoses[0]?.code ?? null,
        preliminaryDiagnosisIcd10Codes: form.diagnoses.map((item) => item.code),
        treatmentCarePerformed: null, version: clinicalData?.version ?? null,
    });

    const save = async (mode, nextStep = null) => {
        const errors = validateExamination(form, mode, icdQuery);
        setFieldErrors(errors);
        if (Object.keys(errors).length) {
            setValidationMode(mode);
            setPageError(pageErrorFor(errors));
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
            // MSG03 for an explicit save; opening the order or conclusion tab saves quietly, the tab shows it worked.
            if (!nextStep) toast.success('Cập nhật hồ sơ khám lâm sàng thành công.');
        } catch (error) {
            setFieldErrors(fieldErrorsFromApi(error));
            setPageError(examinationErrorMessage(error));
            // The next tab opened before the save finished: go back so the Doctor sees what to fix.
            if (nextStep) setActiveSection('clinical', { replace: true });
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

    // The examination is edited only while the patient is in the room; waiting for paraclinical services or back
    // for the conclusion, the Doctor reviews it.
    const waitingParaclinical = visit.status === 'WAITING_PARACLINICAL';
    const waitingConclusion = visit.status === 'WAITING_CONCLUSION';
    const readOnly = visit.status !== 'IN_EXAMINATION';
    // UC-56: nothing waits for paraclinical services (BR-30, BR-41).
    const concludable = visit.status === 'IN_EXAMINATION' || waitingConclusion;
    const transferable = clinicalData?.version === 0 && !loadError && !readOnly;
    // UC-49 AF-04: more services can be ordered while the visit waits for the conclusion (BF-04).
    const orderable = visit.status === 'IN_EXAMINATION' || waitingConclusion;
    // The transfer tab is open only while the visit can still be transferred (e.g. after a reload of its link).
    // When no new order can be made, the order tab shows the ordered list.
    let shownSection = activeSection;
    if (activeSection === 'transfer' && !transferable) shownSection = 'clinical';
    if (activeSection === 'paraclinical' && !orderable) shownSection = 'orders';
    if ((activeSection === 'conclusion' || activeSection === 'prescription' || activeSection === 'follow-up') && !concludable) shownSection = 'clinical';
    const openConclusion = () => {
        if (shownSection === 'conclusion') return;
        if (readOnly) {
            setActiveSection('conclusion');
            return;
        }
        // UC-47 "Save and Continue": the examination must be valid; otherwise the errors show and the tab stays.
        if (Object.keys(validateExamination(form, 'CONTINUE', icdQuery)).length) {
            save('CONTINUE', 'CONCLUSION');
            return;
        }
        setActiveSection('conclusion');
        save('CONTINUE', 'CONCLUSION');
    };
    const openParaclinical = () => {
        if (shownSection === 'paraclinical' || shownSection === 'orders' && !orderable) return;
        if (!orderable) {
            setActiveSection('orders');
            return;
        }
        if (readOnly) {
            // Waiting for the conclusion: the examination is no longer edited, so there is nothing to save first.
            if (!servicesRequest || Date.now() - servicesRequest.at > SERVICES_MAX_AGE_MS) {
                setServicesRequest(startServicesRequest());
            }
            setActiveSection('paraclinical');
            return;
        }
        // AF-04: the examination must be valid (diagnosis required); otherwise the errors show and the tab stays.
        if (Object.keys(validateExamination(form, 'CONTINUE', icdQuery)).length) {
            save('CONTINUE', 'PARACLINICAL');
            return;
        }
        // The list was loaded when the page opened; load it again only if it is getting old.
        if (!servicesRequest || Date.now() - servicesRequest.at > SERVICES_MAX_AGE_MS) {
            setServicesRequest(startServicesRequest());
        }
        // Open the order tab at once and save in the background; ordering waits for the save (see `blocked`).
        setActiveSection('paraclinical');
        save('CONTINUE', 'PARACLINICAL');
    };
    const tabs = [
        [Stethoscope, 'Khám lâm sàng', 'clinical', () => setActiveSection('clinical')],
        [ClipboardPlus, 'Chỉ định cận lâm sàng', 'paraclinical', openParaclinical],
        // The orders already made for the visit, with cancelling a refused service (BR-25, BR-41).
        [ClipboardList, 'Danh sách đã chỉ định', 'orders', () => setActiveSection('orders')],
        // UC-56 Conclude Examination, UC-59 Prescribe Medication, UC-61 Create Follow-up Appointment.
        [FileCheck2, 'Kết luận', 'conclusion', concludable ? openConclusion : null],
        [Pill, 'Kê đơn', 'prescription', concludable ? () => setActiveSection('prescription') : null],
        [CalendarDays, 'Hẹn tái khám', 'follow-up', concludable ? () => setActiveSection('follow-up') : null],
        // UC-48: only before the examination is saved the first time (OQ-05, Q-33).
        [ArrowRightLeft, 'Chuyển phòng', 'transfer', transferable ? openTransfer : null],
    ];

    return (
        <main className="mx-auto max-w-[1600px] space-y-3 pb-8 text-slate-800">
            <header className="-mt-4">
                <div className="flex items-center gap-3"><Stethoscope className="h-7 w-7 text-teal-600" /><div><h1 className="text-2xl font-extrabold">Khám lâm sàng</h1><p className="flex items-center text-xs text-slate-400"><button type="button" onClick={returnToWaitingList} className="cursor-pointer font-semibold text-teal-700 hover:text-teal-900 hover:underline">Khám bệnh</button><span className="px-1">›</span><span>Khám lâm sàng</span></p></div></div>
            </header>

            {waitingParaclinical && <p role="status" className="rounded-xl border border-violet-200 bg-violet-50 px-4 py-2 text-sm text-violet-800">Bệnh nhân đang chờ thực hiện cận lâm sàng. Hồ sơ khám lâm sàng chỉ để xem.</p>}
            {waitingConclusion && <p role="status" className="rounded-xl border border-teal-200 bg-teal-50 px-4 py-2 text-sm text-teal-800">Bệnh nhân đã có đủ kết quả cận lâm sàng. Hồ sơ khám lâm sàng chỉ để xem; thực hiện kết luận ở tab Kết luận.</p>}

            {pageError && <div role="alert" tabIndex={-1} className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-800">{pageError}</div>}

            <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="grid gap-px bg-slate-100 md:grid-cols-3 xl:grid-cols-6">
                    <div className="flex items-center justify-between gap-2.5 bg-white px-3 py-1.5 xl:col-span-2">
                        <div className="flex items-center gap-2.5 min-w-0">
                            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-teal-50 text-teal-700">
                                <UserRound className="h-4 w-4" />
                            </div>
                            <div className="min-w-0">
                                <p className="truncate text-sm font-bold leading-5">{visit.patientName}</p>
                                <p className="text-[11px] leading-4 text-slate-500">Mã BN: <strong>{visit.patientCode}</strong></p>
                            </div>
                        </div>
                        {visit.patientId && (
                            <button
                                type="button"
                                onClick={() => navigate(`/doctor/medical-records?patientId=${encodeURIComponent(visit.patientId)}`)}
                                className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold text-[#127e7a] bg-teal-50 hover:bg-teal-100 rounded-lg transition-colors cursor-pointer shrink-0"
                                title="Xem lịch sử khám của bệnh nhân"
                            >
                                <FolderHeart className="h-3.5 w-3.5" />
                                <span>Lịch sử khám</span>
                            </button>
                        )}
                    </div>
                    <div className="bg-white px-3 py-1.5"><Info label="Ngày sinh / Tuổi">{formatDate(visit.patientDob)} ({ageOf(visit.patientDob)} tuổi)</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Giới tính">{visit.patientGender}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Loại khám">{visitTypeLabel}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Số thẻ BHYT">{visit.mabhyt}</Info></div>
                </div>
                <div className="grid gap-px border-t border-slate-100 bg-slate-100 sm:grid-cols-2 lg:grid-cols-4">
                    <div className="bg-white px-3 py-1.5"><Info label="Nghề nghiệp">{visit.occupation}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Dân tộc">{visit.ethnicity}</Info></div>
                    <div className="bg-white px-3 py-1.5 lg:col-span-2"><Info label="Địa chỉ">{visit.address}</Info></div>
                </div>
                <div className="grid gap-px border-t border-slate-100 bg-slate-100 sm:grid-cols-3 lg:grid-cols-6">
                    <div className="bg-white px-3 py-1.5"><Info label="Mã lượt khám">{visit.sovaovien}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Số thứ tự">{visit.queueNumber}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Phòng khám">{queueInfo?.roomName}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Bác sĩ khám">{queueInfo?.doctorName}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Giờ bắt đầu khám">{formatTime(visit.denkhambenhluc)}</Info></div>
                    <div className="bg-white px-3 py-1.5"><Info label="Trạng thái lượt khám">{statusLabel}</Info></div>
                </div>
            </section>

            <nav className="flex gap-2 overflow-x-auto" aria-label="Các bước khám bệnh">
                {tabs.map(([Icon, label, id, action]) => <button key={label} type="button" onClick={action ?? undefined} disabled={!action || saving} className={`inline-flex min-w-40 items-center justify-center gap-2 rounded-full border px-4 py-2.5 text-xs font-semibold ${shownSection === id ? 'border-teal-700 bg-teal-700 text-white' : 'border-slate-200 bg-white text-slate-500 disabled:opacity-60'}`}><Icon className="h-4 w-4" />{label}</button>)}
            </nav>

            {shownSection === 'prescription' ? (
                <PrescriptionPanel visitId={visit.id} />
            ) : shownSection === 'follow-up' ? (
                <FollowUpPanel visitId={visit.id} />
            ) : shownSection === 'conclusion' ? (
                <ConclusionPanel visitId={visit.id} onClosed={setClosure} onOpenPrescription={() => setActiveSection('prescription')} />
            ) : shownSection === 'transfer' ? (
                <TransferRoomPanel visit={visit} currentRoomName={queueInfo?.roomName} onCancel={() => setActiveSection('clinical')} onDone={goToWaitingList} onVisitChanged={() => setReloadKey((key) => key + 1)} />
            ) : shownSection === 'orders' ? (
                <ParaclinicalOrderList
                    visitId={visit.id}
                    onCancelled={(result) => {
                        // BR-41: no service left to perform, so the visit now waits for the conclusion.
                        if (result?.visitStatus === 'WAITING_CONCLUSION') {
                            toast.success(cancelledMessage(result));
                            goToWaitingList();
                        }
                    }}
                />
            ) : shownSection === 'paraclinical' ? (
                // UC-49: after the order the visit is Waiting for Paraclinical, so the examination closes (MSG67).
                <ParaclinicalOrderPanel visit={visit} initialServices={servicesRequest?.promise} blocked={saving} preliminaryDiagnosis={form.diagnoses.map((item) => item.name).join('; ')} onCancel={() => setActiveSection('clinical')} onCreated={(order) => { toast.success(createdMessage(order)); goToWaitingList(); }} />
            ) : <><div className="grid items-start gap-3 xl:grid-cols-[minmax(0,1fr)_260px]">
                <fieldset disabled={readOnly} className="min-w-0 space-y-3">
                    {form.referralSource === REFERRAL_HEALTH_FACILITY && (
                        // BR-129; TT 32/2023/TT-BYT mẫu 15/BV1 mục I.13: entered at registration, editable here.
                        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="mb-2 text-sm font-bold">Nơi giới thiệu: {REFERRAL_SOURCE_LABELS[form.referralSource]}</h2><Field label="Chẩn đoán của nơi giới thiệu *" value={form.referralDiagnosis} onChange={set('referralDiagnosis')} placeholder="Ghi theo giấy giới thiệu..." maxLength={1000} error={fieldErrors.referralDiagnosis} /></section>
                    )}
                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="mb-2 text-sm font-bold">Lý do vào viện</h2><Field label="Lý do khám / triệu chứng chính *" value={form.reason} onChange={set('reason')} placeholder="Nhập lý do khám, triệu chứng chính..." maxLength={1000} error={fieldErrors.reason} /></section>

                    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                        <h2 className="border-b border-slate-100 px-4 py-3 text-sm font-bold">Lịch sử khám bệnh</h2>
                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[620px] text-left text-xs">
                                <thead className="bg-slate-50 text-slate-500"><tr><th className="w-12 px-4 py-2.5 text-center font-semibold">STT</th><th className="w-32 px-4 py-2.5 font-semibold">Ngày khám</th><th className="px-4 py-2.5 font-semibold">Lý do vào viện</th><th className="px-4 py-2.5 font-semibold">Chẩn đoán</th></tr></thead>
                                <tbody className="divide-y divide-slate-100 text-slate-700">
                                    {(clinicalData?.visitHistory ?? []).map((item, index) => <tr key={item.visitId}><td className="px-4 py-3 text-center text-slate-400">{index + 1}</td><td className="whitespace-nowrap px-4 py-3">{formatDate(item.visitedAt)}</td><td className="px-4 py-3">{item.reasonForVisit || '—'}</td><td className="px-4 py-3">{item.diagnosis || '—'}</td></tr>)}
                                    {!clinicalData?.visitHistory?.length && <tr><td colSpan={4} className="px-4 py-6 text-center text-slate-400">Chưa có lịch sử khám trước đó.</td></tr>}
                                </tbody>
                            </table>
                        </div>
                    </section>

                    <MedicalHistoryPanel visitId={visit.id} />

                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                        <h2 className="mb-3 text-sm font-bold">Khám bệnh</h2>
                        <div className="rounded-xl border border-teal-100 bg-teal-50/40 p-3"><div className="mb-3 flex items-center gap-2 text-xs font-bold text-teal-800"><HeartPulse className="h-4 w-4" />Sinh hiệu <span className="font-normal text-slate-400">• Thời điểm đo: {formatTime(clinicalData?.vitalSigns?.measuredAt ?? new Date())}</span></div><div className="grid grid-cols-2 gap-3 sm:grid-cols-4">{Object.entries(VITAL_RULES).map(([key, [label, unit]]) => <Vital key={key} label={label} value={form[key]} onChange={set(key)} unit={unit} error={fieldErrors[key]} />)}</div>{fieldErrors.vitals && <p className="mt-2 text-[11px] text-rose-600">{fieldErrors.vitals}</p>}<div className="mt-3 flex items-center gap-2 text-xs text-slate-500"><Activity className="h-4 w-4 text-teal-600" />BMI: <strong className="text-slate-700">{bmi.value}</strong> kg/m²{bmi.category && <strong className="text-teal-700">({bmi.category})</strong>}</div></div>
                        <div className="mt-4"><Field label="1. Toàn thân" value={form.generalExam} onChange={set('generalExam')} placeholder="Mô tả tình trạng toàn thân..." maxLength={4000} rows={3} error={fieldErrors.generalExam} /></div>
                        <div className="mt-4"><Field label="2. Khám bộ phận" value={form.organExam} onChange={set('organExam')} placeholder="Nhập kết quả khám bộ phận..." maxLength={2000} rows={4} error={fieldErrors.organExam} /></div>
                        <div className="mt-4"><label className="block text-xs font-semibold text-slate-700">Chẩn đoán ban đầu (ICD-10)<input value={icdQuery} onChange={(event) => changeIcdQuery(event.target.value)} onKeyDown={(event) => {
                            if (event.key === 'Enter' && icdOptions.length) {
                                event.preventDefault();
                                addDiagnosis(icdOptions[0]);
                            }
                        }} placeholder="Nhập mã hoặc tên bệnh để tìm..." aria-invalid={fieldErrors.preliminaryDiagnosis ? 'true' : undefined} className={`mt-1 h-11 w-full rounded-lg border bg-white px-3 text-xs font-normal outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 ${fieldErrors.preliminaryDiagnosis ? 'border-rose-400' : 'border-slate-200'}`} /></label>
                            {icdQuery.trim().length >= 2 && icdOptions.length > 0 && <div className="mt-1 max-h-52 overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-lg">{icdOptions.map((option) => <button key={option.code} type="button" onClick={() => addDiagnosis(option)} disabled={form.diagnoses.some((item) => item.code === option.code)} className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-xs hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-40"><strong className="w-20 shrink-0 text-teal-700">{option.code}</strong><span>{option.name}</span></button>)}</div>}
                            {fieldErrors.preliminaryDiagnosis && <FieldError>{fieldErrors.preliminaryDiagnosis}</FieldError>}
                            <div className="mt-2 space-y-2">{form.diagnoses.map((item) => <div key={item.code} className="flex items-center gap-3 rounded-lg border border-teal-100 bg-teal-50 px-3 py-2 text-xs"><strong className="w-20 shrink-0 text-teal-800">{item.code}</strong><span className="min-w-0 flex-1 text-slate-700">{item.name}</span><button type="button" onClick={() => set('diagnoses')(form.diagnoses.filter((diagnosis) => diagnosis.code !== item.code))} aria-label={`Xóa ${item.code}`} className="rounded p-1 text-slate-400 hover:bg-white hover:text-rose-600"><X className="h-4 w-4" /></button></div>)}</div>
                            <p className="mt-1 text-[10px] font-normal text-slate-500">Có thể chọn nhiều mã ICD-10.</p>
                        </div>
                    </section>
                </fieldset>

                <aside className="sticky top-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><h2 className="flex items-center gap-2 text-sm font-bold"><CalendarDays className="h-4 w-4 text-teal-600" />Thông tin lượt khám</h2><dl className="mt-4 space-y-3 text-xs">{[['Mã lượt khám', visit.sovaovien], ['Ngày khám', formatDate(visit.thoigianvaovien)], ['Giờ đăng ký', formatTime(visit.thoigianvaovien)], ['Giờ bắt đầu khám', formatTime(visit.denkhambenhluc)], ['Số thứ tự', visit.queueNumber], ['Loại khám', visitTypeLabel], ['Phòng khám', queueInfo?.roomName], ['Bác sĩ khám', queueInfo?.doctorName]].map(([label, value]) => <div key={label} className="grid grid-cols-[88px_1fr] gap-2"><dt className="text-slate-400">{label}</dt><dd className="font-semibold text-slate-700">{value || '—'}</dd></div>)}</dl><div className="mt-4 inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700"><span className="h-2 w-2 rounded-full bg-emerald-500" />{statusLabel}</div></aside>
            </div>

            <div className="flex flex-wrap justify-end gap-2"><button type="button" onClick={returnToWaitingList} disabled={saving} className="min-h-11 rounded-lg border border-slate-300 px-4 py-2.5 text-xs font-semibold text-slate-600 disabled:opacity-50">Quay lại danh sách</button>{!readOnly && <button type="button" onClick={saveDraft} disabled={saving} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-5 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50">{saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}{saving ? 'Đang lưu…' : 'Lưu'}</button>}</div>
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
            {closure && <ClosedRecordPrintDialog closure={closure} api={visitApi} onDone={goToWaitingList} />}
        </main>
    );
}
