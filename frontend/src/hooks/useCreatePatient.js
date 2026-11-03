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

/** UC-37 Create New Patient Profile: form state, checks, duplicate warning and saving. */
export function useCreatePatient(patientApi = defaultPatientApi) {
    const navigate = useNavigate();
    const location = useLocation();

    // UC-35 EF-02: the keyword of an unsuccessful search pre-fills the form.
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

    // Step 8–9: create, record (backend) and open UC-36 with MSG04.
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
                // EF-02: MSG19 with Open profile.
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

        // Step 7 / AF-02: warn about profiles with the same full name and date of birth.
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
            // The warning is advisory; creation still checks the identification numbers.
        }
        await save();
    }, [form, patientApi, save, submitting]);

    // AF-03: Cancel asks for confirmation (MSG40) when something was entered.
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
