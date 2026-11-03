import { useCallback, useEffect, useState } from 'react';
import { generatePath, useNavigate } from 'react-router';
import { toast } from 'sonner';
import defaultPatientApi from '../apis/patientApi';
import {
    MSG,
    buildPatientPayload,
    createErrorMessage,
    isDirty,
    msg19,
    serverFieldErrors,
    toFormValues,
    validatePatientForm,
} from '../components/patients/patientForm';
import { clinicToday } from '../components/visits/visitForm';
import { ROUTES } from '../routes/routes';

/** UC-38 Update Patient Information: load, check, save changed fields, back to UC-36. */
export function useUpdatePatient(patientId, patientApi = defaultPatientApi) {
    const navigate = useNavigate();
    const [patient, setPatient] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [initialForm, setInitialForm] = useState(null);
    const [form, setForm] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [conflict, setConflict] = useState(null);
    const [confirmCancel, setConfirmCancel] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    const profilePath = useCallback(
        (id) => generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id }), []);

    // Step 2: the form of UC-37 with the current data.
    useEffect(() => {
        const controller = new AbortController();
        patientApi.detail(patientId, controller.signal)
            .then((res) => {
                const loaded = res?.data ?? res;
                const values = toFormValues(loaded);
                setPatient(loaded);
                setInitialForm(values);
                setForm(values);
            })
            .catch((err) => {
                if (err?.name === 'AbortError') return;
                setLoadError(err?.status === 404 ? MSG.NOT_FOUND : createErrorMessage(err));
            });
        return () => controller.abort();
    }, [patientApi, patientId]);

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

    const handleSubmit = useCallback(async (event) => {
        event.preventDefault();
        if (submitting || !form) return;
        setError('');
        setConflict(null);

        // AF-02: nothing changed, so go back to UC-36 without saving.
        if (!isDirty(form, initialForm)) {
            navigate(profilePath(patientId));
            return;
        }
        // Step 4: the checks of UC-37 steps 4–5.
        const errors = validatePatientForm(form, clinicToday());
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) {
            setError(MSG.FIX_FIELDS);
            return;
        }

        setSubmitting(true);
        try {
            await patientApi.update(patientId, { ...buildPatientPayload(form), version: patient.version });
            // Step 7: MSG03 and back to UC-36 with the new data.
            toast.success(MSG.UPDATED);
            navigate(profilePath(patientId), { replace: true });
        } catch (err) {
            const code = err?.errorResponse?.code;
            const details = err?.errorResponse?.details;
            if (code === 'PATIENT_IDENTIFICATION_EXISTS' && details) {
                // EF-02: MSG19 with Open profile; nothing is saved.
                setConflict(details);
                setFieldErrors((previous) => ({
                    ...previous,
                    [details.field]: msg19(details.field, details.patientCode, details.fullName),
                }));
            } else if (code === 'CONCURRENT_MODIFICATION') {
                setError(MSG.STALE);
            } else {
                setFieldErrors(serverFieldErrors(details));
                setError(createErrorMessage(err));
            }
        } finally {
            setSubmitting(false);
        }
    }, [form, initialForm, navigate, patient, patientApi, patientId, profilePath, submitting]);

    // AF-01: Cancel asks for confirmation (MSG40) when something was changed.
    const goBack = useCallback(() => {
        if (submitting) return;
        if (form && isDirty(form, initialForm)) setConfirmCancel(true);
        else navigate(profilePath(patientId));
    }, [form, initialForm, navigate, patientId, profilePath, submitting]);

    return {
        patient,
        loadError,
        form,
        fieldErrors,
        error,
        conflict,
        confirmCancel,
        submitting,
        handleChange,
        handleSubmit,
        goBack,
        openProfile: (id) => navigate(profilePath(id)),
        keepEditing: () => setConfirmCancel(false),
        discard: () => navigate(profilePath(patientId)),
    };
}
