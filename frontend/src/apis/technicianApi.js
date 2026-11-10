import { apiClient } from '../services/session';

function buildQueueQuery({ roomId, search, status, doctorRoom }) {
  const query = new URLSearchParams();
  if (roomId) query.set('roomId', roomId);
  if (search && search.trim()) query.set('search', search.trim());
  if (status && status !== 'ALL') query.set('status', status);
  if (doctorRoom && doctorRoom !== 'ALL') query.set('doctorRoom', doctorRoom);
  const str = query.toString();
  return str ? `?${str}` : '';
}

export const technicianApi = {
  async getRooms(signal) {
    return apiClient.request('/technician/rooms', { signal });
  },

  async getQueue({ roomId, search, status, doctorRoom } = {}, signal) {
    const queryString = buildQueueQuery({ roomId, search, status, doctorRoom });
    return apiClient.request(`/technician/queue${queryString}`, { signal });
  },

  async getPendingResults({ roomId, search, status, doctorRoom } = {}, signal) {
    const queryString = buildQueueQuery({ roomId, search, status, doctorRoom });
    return apiClient.request(`/technician/results${queryString}`, { signal });
  },

  async getQueueDetail(detailId, signal) {
    return apiClient.request(`/technician/queue/${detailId}`, { signal });
  },

  async acceptOrder(detailId) {
    return apiClient.request(`/technician/queue/${detailId}/accept`, {
      method: 'POST',
    });
  },

  async skipOrder(detailId) {
    return apiClient.request(`/technician/queue/${detailId}/skip`, {
      method: 'POST',
    });
  },

  async unskipOrder(detailId) {
    return apiClient.request(`/technician/queue/${detailId}/unskip`, {
      method: 'POST',
    });
  },

  async completeOrder(detailId) {
    return apiClient.request(`/technician/queue/${detailId}/complete`, {
      method: 'POST',
    });
  },

  /** UC-66 AF-02: back to the waiting list at its original place, with a reason. */
  async returnToWaiting(detailId, reason) {
    return apiClient.request(`/technician/queue/${detailId}/return-to-waiting`, {
      method: 'POST',
      body: { reason },
    });
  },

  async rejectOrder(detailId, reason) {
    return apiClient.request(`/technician/queue/${detailId}/reject`, {
      method: 'POST',
      body: { reason },
    });
  },
};

export default technicianApi;

