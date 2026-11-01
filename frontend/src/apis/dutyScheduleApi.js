import { apiClient } from '../services/session';

/**
 * API lịch trực. Không có mock/fallback: mọi lỗi từ backend (mạng, 4xx, 5xx)
 * đều được ném nguyên vẹn (ApiError) để tầng UI hiển thị, giúp phân biệt lỗi FE/BE.
 * apiClient đã tự bóc `ApiResponse.data`, nên các hàm trả về đúng payload của backend.
 */

function buildRangeQuery(startDate, endDate) {
  const query = new URLSearchParams();
  if (startDate) query.set('startDate', startDate);
  if (endDate) query.set('endDate', endDate);
  return query.toString();
}

function toDutyPayload(item) {
  return {
    id: item.id || undefined,
    workDate: item.workDate,
    shift: item.shift,
    staffAccountId: item.staffAccountId,
    roomId: item.roomId ? item.roomId : null,
    notes: item.notes || '',
  };
}

export const dutyScheduleApi = {
  async getWeeklySchedule(startDate, endDate, signal) {
    return apiClient.request(`/duty-schedules?${buildRangeQuery(startDate, endDate)}`, { signal });
  },

  async getWeeklyScheduleByRoom(roomId, startDate, endDate, signal) {
    return apiClient.request(
      `/duty-schedules/rooms/${roomId}?${buildRangeQuery(startDate, endDate)}`,
      { signal }
    );
  },

  async saveDuty(data) {
    return apiClient.request('/duty-schedules', {
      method: 'POST',
      body: toDutyPayload(data),
    });
  },

  async batchSaveWeeklySchedule(data) {
    return apiClient.request('/duty-schedules/weekly', {
      method: 'PUT',
      body: {
        startDate: data.startDate,
        endDate: data.endDate,
        items: (data.items || []).map(toDutyPayload),
      },
    });
  },

  async deleteDuty(id) {
    return apiClient.request(`/duty-schedules/${id}`, {
      method: 'DELETE',
    });
  },
};

export default dutyScheduleApi;
