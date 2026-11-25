import { useCallback, useEffect, useState } from 'react';
import { generatePath, useNavigate } from 'react-router';
import { toast } from 'sonner';
import defaultPatientApi from '../apis/patientApi';
import {
    MSG,
    buildPatientPayload,
    createErrorMessage,
    formatFullName,
    isDirty,
    msg19,
    serverFieldErrors,
    toFormValues,
    validatePatientForm,
    withDerivedCodes,
} from '../components/patients/patientForm';
import { clinicToday } from '../components/visits/visitForm';
import { ROUTES } from '../routes/routes';

// UC-38: tai ho so, kiem tra, luu cac truong da sua, quay ve ho so
export function useUpdatePatient(patientId, patientApi = defaultPatientApi) {
    const navigate = useNavigate();
    const [patient, setPatient] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [initialForm, setInitialForm] = useState(null);
    const [form, setForm] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [conflict, setConflict] = useState(null);
    const [discardDestination, setDiscardDestination] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const profilePath = useCallback(
        (id) => generatePath(ROUTES.RECEPTIONIST.PATIENT_DETAIL, { id }), []);

    // tai ho so hien tai vao form
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
        setForm((previous) => withDerivedCodes(previous, name, value));
        setFieldErrors((previous) => {
            if (!previous[name]) return previous;
            const next = { ...previous };
            delete next[name];
            return next;
        });
        setError('');
        if (name === 'cccdSo' || name === 'hochieuSo') setConflict(null);
    }, []);

    const handleBlur = useCallback((event) => {
        const { name } = event.target;
        if (!name || !form) return;

        let nextForm = form;
        if ((name === 'hoten' || name === 'hotennguoithan') && form[name]) {
            const formatted = formatFullName(form[name]);
            if (formatted !== form[name]) {
                nextForm = { ...form, [name]: formatted };
                setForm(nextForm);
            }
        }

        const errors = validatePatientForm(nextForm, clinicToday());
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

    const handleSubmit = useCallback(async (event) => {
        event.preventDefault();
        if (submitting || !form) return;
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

        // khong sua gi thi quay ve, khong luu
        if (!isDirty(currentForm, initialForm)) {
            navigate(profilePath(patientId));
            return;
        }
        // kiem tra giong luc tao ho so
        const errors = validatePatientForm(currentForm, clinicToday());
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) {
            setError(MSG.FIX_FIELDS);
            return;
        }

        setSubmitting(true);
        try {
            await patientApi.update(patientId, { ...buildPatientPayload(currentForm), version: patient.version });
            // luu xong: MSG03 va quay ve ho so
            toast.success(MSG.UPDATED);
            navigate(profilePath(patientId), { replace: true });
        } catch (err) {
            const code = err?.errorResponse?.code;
            const details = err?.errorResponse?.details;
            if (code === 'PATIENT_IDENTIFICATION_EXISTS' && details) {
                // trung CCCD / ho chieu: hien MSG19, khong luu
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

    // huy khi da sua thi hoi xac nhan (MSG40)
    const leaveForm = (destination) => {
        if (submitting) return;
        if (form && isDirty(form, initialForm)) setDiscardDestination(destination);
        else navigate(destination);
    };

    return {
        patient,
        loadError,
        form,
        fieldErrors,
        error,
        conflict,
        confirmCancel: Boolean(discardDestination),
        submitting,
        handleChange,
        handleBlur,
        handleSubmit,
        goBack: () => leaveForm(profilePath(patientId)),
        goToPatients: () => leaveForm(ROUTES.RECEPTIONIST.PATIENTS),
        goToOverview: () => leaveForm(ROUTES.RECEPTIONIST.OVERVIEW),
        openProfile: (id) => navigate(profilePath(id)),
        keepEditing: () => setDiscardDestination(null),
        discard: () => navigate(discardDestination),
    };
}
