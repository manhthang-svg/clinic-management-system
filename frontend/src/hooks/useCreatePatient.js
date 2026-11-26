import { useCallback, useState } from 'react';
import { generatePath, useLocation, useNavigate } from 'react-router';
import { toast } from 'sonner';
import defaultPatientApi from '../apis/patientApi';
import {
    INITIAL_PATIENT_FORM,
    MSG,
    buildPatientPayload,
    clearPatientFieldErrors,
    createErrorMessage,
    formatFullName,
    isDirty,
    msg19,
    serverFieldErrors,
    validateCreatePatientForm,
    withDerivedCodes,
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
    const [discardDestination, setDiscardDestination] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const handleChange = useCallback((event) => {
        const { name, value } = event.target;
        setForm((previous) => withDerivedCodes(previous, name, value));
        const nextForm = withDerivedCodes(form, name, value);
        const today = clinicToday();
        setFieldErrors((previous) => clearPatientFieldErrors(previous, name, nextForm, today, validateCreatePatientForm));
        setError('');
        if (name === 'cccdSo' || name === 'hochieuSo') setConflict(null);
    }, [form]);

    const handleBlur = useCallback((event) => {
        const { name } = event.target;
        if (!name) return;

        let nextForm = form;
        if ((name === 'hoten' || name === 'hotennguoithan') && form[name]) {
            const formatted = formatFullName(form[name]);
            if (formatted !== form[name]) {
                nextForm = { ...form, [name]: formatted };
                setForm(nextForm);
            }
        }

        const errors = validateCreatePatientForm(nextForm, clinicToday());
        const fieldError = errors[name];
        setFieldErrors((previous) => {
            if (fieldError) {
                return { ...previous, [name]: fieldError };
            }
            if (previous[name]) {
                const next = { ...previous };
                delete next[name];
                return next;
            }
            return previous;
        });
    }, [form]);

    const openProfile = useCallback((patientId) => {
        navigate(generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id: patientId }));
    }, [navigate]);

    // luu ho so, xong thi mo trang ho so (MSG04)
    const save = useCallback(async (formToSave = form) => {
        setDuplicates(null);
        setSubmitting(true);
        try {
            const res = await patientApi.create(buildPatientPayload(formToSave));
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

        let currentForm = form;
        const formattedHoten = formatFullName(form.hoten);
        const formattedNguoithan = formatFullName(form.hotennguoithan);
        if (formattedHoten !== form.hoten || formattedNguoithan !== form.hotennguoithan) {
            currentForm = {
                ...form,
                hoten: formattedHoten,
                hotennguoithan: formattedNguoithan,
            };
            setForm(currentForm);
        }

        const errors = validateCreatePatientForm(currentForm, clinicToday());
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) {
            setError(MSG.FIX_FIELDS);
            return;
        }

        // canh bao ho so trung ho ten + ngay sinh truoc khi luu
        setSubmitting(true);
        try {
            const res = await patientApi.possibleDuplicates(currentForm.hoten.trim(), currentForm.ngaysinh);
            const list = res?.data ?? res ?? [];
            if (list.length > 0) {
                setDuplicates(list);
                setSubmitting(false);
                return;
            }
        } catch {
            // chi canh bao, van cho tao tiep
        }
        await save(currentForm);
    }, [form, patientApi, save, submitting]);

    // huy khi da nhap du lieu thi hoi xac nhan (MSG40)
    const leaveForm = (destination) => {
        if (submitting) return;
        if (isDirty(form, initialForm)) setDiscardDestination(destination);
        else navigate(destination);
    };

    return {
        form,
        fieldErrors,
        error,
        conflict,
        duplicates,
        confirmCancel: Boolean(discardDestination),
        submitting,
        handleChange,
        handleBlur,
        handleSubmit,
        goBack: () => leaveForm(ROUTES.RECEPTIONIST.PATIENTS),
        goToOverview: () => leaveForm(ROUTES.RECEPTIONIST.OVERVIEW),
        openProfile,
        createAnyway: save,
        closeDuplicates: () => setDuplicates(null),
        keepEditing: () => setDiscardDestination(null),
        discard: () => navigate(discardDestination),
    };
}
