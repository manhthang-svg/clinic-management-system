import { apiClient } from '../services/session';

/**
 * API khoa/phòng. Không có mock/fallback: mọi lỗi từ backend (mạng, 4xx, 5xx)
 * đều được ném nguyên vẹn (ApiError) để tầng UI hiển thị, giúp phân biệt lỗi FE/BE.
 */
export const departmentApi = {
  async getItems() {
    return apiClient.request('/departments/items');
  },

  async getAllItems() {
    return this.getItems();
  },

  async getStats() {
    return apiClient.request('/departments/stats');
  },

  async createDepartment(data) {
    return apiClient.request('/departments', {
      method: 'POST',
      body: data,
    });
  },

  async updateDepartment(id, data) {
    return apiClient.request(`/departments/${id}`, {
      method: 'PUT',
      body: data,
    });
  },

  async toggleDepartmentStatus(id) {
    return apiClient.request(`/departments/${id}/status`, {
      method: 'PATCH',
    });
  },

  async createRoom(data) {
    return apiClient.request('/departments/rooms', {
      method: 'POST',
      body: data,
    });
  },

  async updateRoom(id, data) {
    return apiClient.request(`/departments/rooms/${id}`, {
      method: 'PUT',
      body: data,
    });
  },

  async toggleRoomStatus(id) {
    return apiClient.request(`/departments/rooms/${id}/status`, {
      method: 'PATCH',
    });
  },
};

export default departmentApi;
