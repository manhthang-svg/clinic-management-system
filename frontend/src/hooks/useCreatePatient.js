import { useCallback, useState } from 'react';
import { generatePath, useLocation, useNavigate } from 'react-router';
import { toast } from 'sonner';
import defaultPatientApi from '../apis/patientApi';
import {
    INITIAL_PATIENT_FORM,
    MSG,
    buildPatientPayload,
    createErrorMessage,
    isDirty,
    msg19,
    serverFieldErrors,
    validatePatientForm,
} from '../components/patients/patientForm';
import { clinicToday } from '../components/visits/visitForm';
import { ROUTES } from '../routes/routes';

// UC-37: state form tao ho so, kiem tra, canh bao trung va luu
export function useCreatePatient(patientApi = defaultPatientApi) {
    const navigate = useNavigate();
    const location = useLocation();

    // tu khoa tim khong ra duoc dien san vao form
    const [initialForm] = useState(() => ({ ...INITIAL_PATIENT_FORM, ...(location.state?.prefill ?? {}) }));
    const [form, setForm] = useState(initialForm);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [conflict, setConflict] = useState(null);
    const [duplicates, setDuplicates] = useState(null);
    const [confirmCancel, setConfirmCancel] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    const handleChange = useCallback((event) => {
        const { name, value } = event.target;
        setForm((previous) => ({ ...previous, [name]: value }));
        setFieldErrors((previous) => {
            if (!previous[name]) return previous;
            const next = { ...previous };
            delete next[name];
            return next;
        });
        setError('');
        if (name === 'cccdSo' || name === 'hochieuSo') setConflict(null);
    }, []);

    const openProfile = useCallback((patientId) => {
        navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id: patientId }));
    }, [navigate]);

    // luu ho so, xong thi mo trang ho so (MSG04)
    const save = useCallback(async () => {
        setDuplicates(null);
        setSubmitting(true);
        try {
            const res = await patientApi.create(buildPatientPayload(form));
            const created = res?.data || res;
            toast.success(MSG.CREATED);
            navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id: created.id }), { replace: true });
        } catch (err) {
            const details = err?.errorResponse?.details;
            if (err?.errorResponse?.code === 'PATIENT_IDENTIFICATION_EXISTS' && details) {
                // trung CCCD / ho chieu: hien MSG19 + nut mo ho so
                setConflict(details);
                setFieldErrors((previous) => ({
                    ...previous,
                    [details.field]: msg19(details.field, details.patientCode, details.fullName),
                }));
            } else {
                setFieldErrors(serverFieldErrors(details));
                setError(createErrorMessage(err));
            }
        } finally {
            setSubmitting(false);
        }
    }, [form, navigate, patientApi]);

    const handleSubmit = useCallback(async (event) => {
        event.preventDefault();
        if (submitting) return;
        setError('');
        setConflict(null);

        const errors = validatePatientForm(form, clinicToday());
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) {
            setError(MSG.FIX_FIELDS);
            return;
        }

        // canh bao ho so trung ho ten + ngay sinh truoc khi luu
        setSubmitting(true);
        try {
            const res = await patientApi.possibleDuplicates(form.hoten.trim(), form.ngaysinh);
            const list = res?.data ?? res ?? [];
            if (list.length > 0) {
                setDuplicates(list);
                setSubmitting(false);
                return;
            }
        } catch {
            // chi canh bao, van cho tao tiep
        }
        await save();
    }, [form, patientApi, save, submitting]);

    // huy khi da nhap du lieu thi hoi xac nhan (MSG40)
    const goBack = useCallback(() => {
        if (submitting) return;
        if (isDirty(form, initialForm)) setConfirmCancel(true);
        else navigate(ROUTES.RECEPTIONIST.PATIENTS);
    }, [form, initialForm, navigate, submitting]);

    return {
        form,
        fieldErrors,
        error,
        conflict,
        duplicates,
        confirmCancel,
        submitting,
        handleChange,
        handleSubmit,
        goBack,
        openProfile,
        createAnyway: save,
        closeDuplicates: () => setDuplicates(null),
        keepEditing: () => setConfirmCancel(false),
        discard: () => navigate(ROUTES.RECEPTIONIST.PATIENTS),
    };
}
