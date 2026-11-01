import { apiClient } from '../services/session';

// In-memory mock storage fallback for offline / development
let mockScheduleItems = [
  {
    id: 'duty-mock-1',
    workDate: new Date().toISOString().split('T')[0],
    shift: 'MORNING',
    staffAccountId: 'staff-01',
    staffName: 'BSCKII Trần Văn An',
    staffUsername: 'an.tran',
    staffRole: 'DOCTOR',
    roomId: 'room-01',
    roomName: 'Phòng Khám Nội 1',
    roomCode: 'PK-N01',
    notes: 'Trực chính ca sáng',
    assignedByName: 'Quản trị viên',
  },
  {
    id: 'duty-mock-2',
    workDate: new Date().toISOString().split('T')[0],
    shift: 'AFTERNOON',
    staffAccountId: 'staff-02',
    staffName: 'BSCKI Phạm Minh Châu',
    staffUsername: 'chau.pham',
    staffRole: 'DOCTOR',
    roomId: 'room-02',
    roomName: 'Phòng Khám Ngoại',
    roomCode: 'PK-NG01',
    notes: 'Trực ca chiều',
    assignedByName: 'Quản trị viên',
  },
];

export const dutyScheduleApi = {
  async getWeeklySchedule(startDate, endDate, signal) {
    try {
      const query = new URLSearchParams();
      if (startDate) query.set('startDate', startDate);
      if (endDate) query.set('endDate', endDate);
      const res = await apiClient.request(`/duty-schedules?${query.toString()}`, { signal });
      return res?.data || res;
    } catch (err) {
      if (err?.status) {
        throw err;
      }
      console.warn('Backend duty-schedules unreachable, using mock data:', err);
      const filtered = mockScheduleItems.filter(
        item => (!startDate || item.workDate >= startDate) && (!endDate || item.workDate <= endDate)
      );
      return {
        startDate,
        endDate,
        items: filtered,
      };
    }
  },

  async getWeeklyScheduleByRoom(roomId, startDate, endDate, signal) {
    try {
      const query = new URLSearchParams();
      if (startDate) query.set('startDate', startDate);
      if (endDate) query.set('endDate', endDate);
      const res = await apiClient.request(`/duty-schedules/rooms/${roomId}?${query.toString()}`, { signal });
      return res?.data || res;
    } catch (err) {
      if (err?.status) {
        throw err;
      }
      console.warn('Backend duty-schedules by room unreachable, using mock data:', err);
      const filtered = mockScheduleItems.filter(
        item => item.roomId === roomId && (!startDate || item.workDate >= startDate) && (!endDate || item.workDate <= endDate)
      );
      return {
        startDate,
        endDate,
        items: filtered,
      };
    }
  },

  async saveDuty(data) {
    const payload = {
      id: data.id || undefined,
      workDate: data.workDate,
      shift: data.shift,
      staffAccountId: data.staffAccountId,
      roomId: data.roomId ? data.roomId : null,
      notes: data.notes || '',
    };
    try {
      const res = await apiClient.request('/duty-schedules', {
        method: 'POST',
        body: payload,
      });
      return res?.data || res;
    } catch (err) {
      if (err?.status) {
        throw err;
      }
      console.warn('Backend saveDuty fallback to local mock:', err);
      const existingIdx = mockScheduleItems.findIndex(
        s => s.id === data.id || (s.workDate === data.workDate && s.shift === data.shift && s.staffAccountId === data.staffAccountId)
      );
      const savedItem = {
        id: data.id || `duty-${Date.now()}`,
        ...data,
        roomId: data.roomId || null,
      };
      if (existingIdx >= 0) {
        mockScheduleItems[existingIdx] = { ...mockScheduleItems[existingIdx], ...savedItem };
      } else {
        mockScheduleItems.push(savedItem);
      }
      return savedItem;
    }
  },

  async batchSaveWeeklySchedule(data) {
    const payload = {
      startDate: data.startDate,
      endDate: data.endDate,
      items: (data.items || []).map(it => ({
        id: it.id || undefined,
        workDate: it.workDate,
        shift: it.shift,
        staffAccountId: it.staffAccountId,
        roomId: it.roomId ? it.roomId : null,
        notes: it.notes || '',
      })),
    };
    try {
      const res = await apiClient.request('/duty-schedules/weekly', {
        method: 'PUT',
        body: payload,
      });
      return res?.data || res;
    } catch (err) {
      if (err?.status) {
        throw err;
      }
      console.warn('Backend batchSave fallback to local mock:', err);
      const { startDate, endDate, items } = data;
      mockScheduleItems = mockScheduleItems.filter(
        item => item.workDate < startDate || item.workDate > endDate
      );
      (items || []).forEach(it => {
        mockScheduleItems.push({
          id: it.id || `duty-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
          ...it,
          roomId: it.roomId || null,
        });
      });
      return {
        startDate,
        endDate,
        items: mockScheduleItems.filter(
          item => item.workDate >= startDate && item.workDate <= endDate
        ),
      };
    }
  },

  async deleteDuty(id) {
    try {
      const res = await apiClient.request(`/duty-schedules/${id}`, {
        method: 'DELETE',
      });
      return res?.data || res;
    } catch (err) {
      if (err?.status) {
        throw err;
      }
      console.warn('Backend deleteDuty fallback to local mock:', err);
      mockScheduleItems = mockScheduleItems.filter(item => item.id !== id);
      return { success: true };
    }
  },
};

export default dutyScheduleApi;
