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

    // Paraclinical orders already made for a visit (Doctor of that visit only).
    getParaclinicalOrders(visitId, signal) {
        return apiClient.request(`/paraclinical-orders?visitId=${encodeURIComponent(visitId)}`, { signal });
    },

    // BR-25, BR-41: the Doctor cancels a service the patient refused, with a mandatory reason.
    cancelParaclinicalService(orderDetailId, reason) {
        return apiClient.request(`/paraclinical-orders/details/${encodeURIComponent(orderDetailId)}/commands/cancel`, {
            method: 'POST',
            body: { reason },
        });
    },

    getParaclinicalServices(signal) {
        return apiClient.request('/paraclinical-orders/services', { signal });
    },

    // UC-50: content of the order slip; the server records the print (BR-50-08).
    prepareParaclinicalOrderPrint(orderId) {
        return apiClient.request(`/paraclinical-orders/${encodeURIComponent(orderId)}/commands/prepare-print`, {
            method: 'POST',
        });
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

    // UC-39: medical history and drug allergies of the patient of a visit (Doctor only).
    getMedicalHistory(visitId, signal) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/medical-history`, { signal });
    },

    updateMedicalHistory(visitId, data) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/medical-history`, {
            method: 'PUT',
            body: data,
        });
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

    acceptSkippedVisit(visitId) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/accept-skipped`, {
            method: 'POST',
        });
    },

    getTransferRooms(visitId, signal) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/transfer-rooms`, { signal });
    },

    transferVisit(visitId, payload) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/transfer`, {
            method: 'POST',
            body: payload,
        });
    },

    openClinicalExamination(visitId, signal) {
        return apiClient.request(`/clinical-examinations/visits/${encodeURIComponent(visitId)}/open`, {
            method: 'POST',
            signal,
        });
    },

    saveClinicalExamination(visitId, payload) {
        return apiClient.request(`/clinical-examinations/visits/${encodeURIComponent(visitId)}`, {
            method: 'PUT',
            body: payload,
        });
    },

    // UC-56: the conclusion form of a visit, with any saved data.
    getConclusion(visitId, signal) {
        return apiClient.request(`/conclusions/visits/${encodeURIComponent(visitId)}`, { signal });
    },

    saveConclusion(visitId, payload) {
        return apiClient.request(`/conclusions/visits/${encodeURIComponent(visitId)}`, {
            method: 'PUT',
            body: payload,
        });
    },

    // UC-59: the visit's prescription form, with the conclusion diagnoses and drug allergies.
    getPrescription(visitId, signal) {
        return apiClient.request(`/prescriptions/visits/${encodeURIComponent(visitId)}`, { signal });
    },

    savePrescription(visitId, payload) {
        return apiClient.request(`/prescriptions/visits/${encodeURIComponent(visitId)}`, {
            method: 'PUT',
            body: payload,
        });
    },

    // Active ordinary medicines (BR-33, BR-52) by name, active ingredient or code.
    searchMedicines(keyword, signal) {
        const query = new URLSearchParams({ keyword, limit: '10' });
        return apiClient.request(`/prescriptions/medicines?${query.toString()}`, { signal });
    },

    searchIcd10(keyword, signal) {
        const query = new URLSearchParams({ keyword, limit: '10' });
        return apiClient.request(`/clinical-examinations/icd10?${query.toString()}`, { signal });
    },
};

export default visitApi;
