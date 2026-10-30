import { apiClient } from '../services/session';

export const visitApi = {
    create(data) {
        return apiClient.request('/visits', {
            method: 'POST',
            body: data,
        });
    },

    getActiveExaminationRooms(signal) {
        return apiClient.request('/rooms/active-examination', { signal });
    },

    orderParaclinical(data) {
        return apiClient.request('/paraclinical-orders', {
            method: 'POST',
            body: data,
        });
    },

    getParaclinicalServices(signal) {
        return apiClient.request('/paraclinical-orders/services', { signal });
    },

    getQueue(params = {}, signal) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
                query.set(key, value);
            }
        });
        const suffix = query.size ? `?${query.toString()}` : '';
        return apiClient.request(`/visits/queue${suffix}`, { signal });
    },

    // UC-42: counts per active examination room, for the receptionist.
    getQueueStatus(signal) {
        return apiClient.request('/visits/queue-status', { signal });
    },

    // TBD (UC-74): the backend has no insurance endpoint yet; path and response shape are a proposal.
    // Response: { status, fullName, dateOfBirth, benefitCode, coverageRate, validFrom, validTo, primaryCareFacility }.
    verifyInsurance(data) {
        return apiClient.request('/insurance/verifications', {
            method: 'POST',
            body: data,
        });
    },

    // TBD (BR-27): proposed fee preview for the confirmation step.
    // Response: { serviceName, clinicPrice, insuranceAmount, patientAmount }.
    feePreview(params = {}, signal) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') query.set(key, value);
        });
        return apiClient.request(`/visits/fee-preview?${query.toString()}`, { signal });
    },

    // TBD (UC-40 AF-04/05): proposed lookup of the patient's scheduled follow-up appointment.
    // Response: { id, appointmentDate, doctorName, suggestedRoomId, suggestedRoomName, status } or null.
    getFollowUpAppointment(patientId, signal) {
        return apiClient.request(`/patients/${encodeURIComponent(patientId)}/follow-up-appointment`, { signal });
    },

    // TBD (UC-40 AF-06): proposed cancellation of a follow-up appointment with a reason.
    cancelFollowUpAppointment(appointmentId, reason) {
        return apiClient.request(`/follow-up-appointments/${encodeURIComponent(appointmentId)}/cancel`, {
            method: 'POST',
            body: { reason },
        });
    },

    startExamination(visitId) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/start-examination`, {
            method: 'POST',
        });
    },

    skipVisit(visitId) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/skip`, {
            method: 'POST',
        });
    },
};

export default visitApi;
