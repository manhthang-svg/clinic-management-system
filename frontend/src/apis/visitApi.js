import { apiClient } from '../services/session';

export const visitApi = {
    create(data) {
        return apiClient.request('/visits', {
            method: 'POST',
            body: data,
        });
    },

    getActiveVisit(patientId, signal) {
        return apiClient.request(`/visits/active?patientId=${encodeURIComponent(patientId)}`, { signal });
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

    // UC-43: authenticated doctor's assigned-room queue with server-side filtering and pagination.
    getDoctorQueue(params = {}, signal) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
                query.set(key, value);
            }
        });
        const suffix = query.size ? `?${query.toString()}` : '';
        return apiClient.request(`/visits/doctor-queue${suffix}`, { signal });
    },

    // UC-42: counts per active examination room, for the receptionist.
    getQueueStatus(signal) {
        return apiClient.request('/visits/queue-status', { signal });
    },

    verifyInsurance(data, signal) {
        return apiClient.request('/insurance/verifications', {
            method: 'POST',
            body: data,
            signal,
        });
    },

    feePreview(params = {}, signal) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') query.set(key, value);
        });
        return apiClient.request(`/visits/fee-preview?${query.toString()}`, { signal });
    },

    getTicket(visitId, signal) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/ticket`, { signal });
    },

    prepareTicketPrint(visitId) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/commands/prepare-ticket-print`, {
            method: 'POST',
        });
    },

    getFollowUpAppointment(patientId, signal) {
        return apiClient.request(`/patients/${encodeURIComponent(patientId)}/follow-up-appointment`, { signal });
    },

    cancelFollowUpAppointment(appointmentId, reason, version) {
        return apiClient.request(`/follow-up-appointments/${encodeURIComponent(appointmentId)}/cancel`, {
            method: 'POST',
            body: { reason, version },
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
