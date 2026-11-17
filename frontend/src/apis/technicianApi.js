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

// In-memory cache & active room selection cho khối kỹ thuật viên
let cachedRooms = null;
let cachedRoomsPromise = null;
let lastRoomsFetchTime = 0;
const ROOMS_CACHE_TTL = 30_000; // 30s
const ACTIVE_ROOM_KEY = 'technician_active_room_id';

// de giup chuyen tab lien tuc ma van giu nguyen phong minh da chon 
export const technicianApi = {
  getActiveRoomId() {
    try {
      return sessionStorage.getItem(ACTIVE_ROOM_KEY) || '';
    } catch {
      return '';
    }
  },

  setActiveRoomId(roomId) {
    try {
      if (roomId) {
        sessionStorage.setItem(ACTIVE_ROOM_KEY, roomId);
      } else {
        sessionStorage.removeItem(ACTIVE_ROOM_KEY);
      }
    } catch {
      // Ignore storage errors
    }
  },

  getCachedRooms() {
    return cachedRooms;
  },

  async getRooms(signal, { force = false } = {}) {
    const now = Date.now();
    if (!force && cachedRooms && now - lastRoomsFetchTime < ROOMS_CACHE_TTL) {
      // tra ve ngay data neu lần tải gần nhát nhỏ hơn 30s
      return cachedRooms;
    }
    if (cachedRoomsPromise && !force) {
      return cachedRoomsPromise;// Tái sử dụng Promise đang chạy
    }

    cachedRoomsPromise = apiClient
      .request('/technician/rooms', { signal })
      .then((data) => {
        cachedRooms = Array.isArray(data) ? data : [];
        lastRoomsFetchTime = Date.now();
        return cachedRooms;
      })
      .finally(() => {
        cachedRoomsPromise = null;
      });

    return cachedRoomsPromise;
  },

  /** Dashboard of one room: today's figures, the next 5 waiting patients and 5 services waiting for a result. */
  async getDashboard({ roomId } = {}, signal) {
    const queryString = buildQueueQuery({ roomId });
    return apiClient.request(`/technician/dashboard${queryString}`, { signal });
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

  async checkInOrder(detailId) {
    return apiClient.request(`/technician/queue/${detailId}/check-in`, {
      method: 'POST',
    });
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

