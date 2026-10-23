import { apiClient } from '../services/session';

export const INITIAL_DEPARTMENT_ITEMS = [
  // Khoa
  {
    id: 'dept-01',
    unitType: 'DEPARTMENT',
    code: 'DEMO-NOI',
    name: 'Khoa Nội',
    departmentId: 'dept-01',
    departmentName: 'Khoa Nội',
    type: 'CLINICAL',
    location: 'Tòa A - Tầng 1',
    headPerson: 'BSCKII Trần Văn An',
    status: 'ACTIVE',
    description: 'Khám và điều trị nội khoa: cơ xương khớp, nội tiết, tiêu hóa, nội tổng quát',
    roomCount: 5,
  },
  {
    id: 'dept-02',
    unitType: 'DEPARTMENT',
    code: 'DEMO-NGOAI',
    name: 'Khoa Ngoại',
    departmentId: 'dept-02',
    departmentName: 'Khoa Ngoại',
    type: 'CLINICAL',
    location: 'Tòa A - Tầng 2',
    headPerson: 'BSCKI Phạm Minh Châu',
    status: 'ACTIVE',
    description: 'Khám ngoại khoa tổng quát và tiểu phẫu',
    roomCount: 1,
  },
  {
    id: 'dept-03',
    unitType: 'DEPARTMENT',
    code: 'DEMO-XN',
    name: 'Khoa Xét nghiệm',
    departmentId: 'dept-03',
    departmentName: 'Khoa Xét nghiệm',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 1',
    headPerson: 'KTV Nguyễn Văn Bảo',
    status: 'ACTIVE',
    description: 'Xét nghiệm huyết học, sinh hóa, vi sinh',
    roomCount: 3,
  },
  {
    id: 'dept-04',
    unitType: 'DEPARTMENT',
    code: 'DEMO-CDHA',
    name: 'Khoa Chẩn đoán hình ảnh',
    departmentId: 'dept-04',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 2',
    headPerson: 'KTV Trần Thị Cúc',
    status: 'ACTIVE',
    description: 'Siêu âm màu, X-quang kỹ thuật số, CT, MRI',
    roomCount: 5,
  },
  {
    id: 'dept-05',
    unitType: 'DEPARTMENT',
    code: 'DEMO-TDCN',
    name: 'Khoa Thăm dò chức năng',
    departmentId: 'dept-05',
    departmentName: 'Khoa Thăm dò chức năng',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 3',
    headPerson: 'KTV Lê Thị Đào',
    status: 'ACTIVE',
    description: 'Nội soi tiêu hóa, điện tâm đồ',
    roomCount: 2,
  },

  // Phòng
  {
    id: 'room-01',
    unitType: 'ROOM',
    code: 'PK-NOI-CXK',
    name: 'Phòng khám Cơ xương khớp',
    departmentId: 'dept-01',
    departmentName: 'Khoa Nội',
    type: 'EXAMINATION',
    location: 'Tòa A - Tầng 1 - P.101',
    headPerson: 'BSCKII Trần Văn An',
    status: 'ACTIVE',
  },
  {
    id: 'room-02',
    unitType: 'ROOM',
    code: 'PK-NOI-NT',
    name: 'Phòng khám Nội tiết',
    departmentId: 'dept-01',
    departmentName: 'Khoa Nội',
    type: 'EXAMINATION',
    location: 'Tòa A - Tầng 1 - P.102',
    headPerson: 'ThS.BS Lê Thị Bình',
    status: 'ACTIVE',
  },
  {
    id: 'room-03',
    unitType: 'ROOM',
    code: 'PK-NOI-TH',
    name: 'Phòng khám Tiêu hóa',
    departmentId: 'dept-01',
    departmentName: 'Khoa Nội',
    type: 'EXAMINATION',
    location: 'Tòa A - Tầng 1 - P.103',
    headPerson: 'ThS.BS Nguyễn Văn An',
    status: 'ACTIVE',
  },
  {
    id: 'room-04',
    unitType: 'ROOM',
    code: 'PK-NOI-TQ',
    name: 'Phòng khám Nội tổng quát',
    departmentId: 'dept-01',
    departmentName: 'Khoa Nội',
    type: 'EXAMINATION',
    location: 'Tòa A - Tầng 1 - P.104',
    headPerson: 'BSCKII Trần Văn An',
    status: 'ACTIVE',
  },
  {
    id: 'room-05',
    unitType: 'ROOM',
    code: 'PK-NGOAI',
    name: 'Phòng khám Ngoại',
    departmentId: 'dept-02',
    departmentName: 'Khoa Ngoại',
    type: 'EXAMINATION',
    location: 'Tòa A - Tầng 2 - P.201',
    headPerson: 'BSCKI Phạm Minh Châu',
    status: 'ACTIVE',
  },
  {
    id: 'room-06',
    unitType: 'ROOM',
    code: 'PK-NOI-TQ-2',
    name: 'Phòng khám Nội tổng quát 2',
    departmentId: 'dept-01',
    departmentName: 'Khoa Nội',
    type: 'EXAMINATION',
    location: 'Tòa A - Tầng 1 - P.105',
    headPerson: 'Chưa phân công',
    status: 'INACTIVE',
  },
  {
    id: 'room-07',
    unitType: 'ROOM',
    code: 'XN-HH',
    name: 'Phòng Xét nghiệm Huyết học',
    departmentId: 'dept-03',
    departmentName: 'Khoa Xét nghiệm',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 1 - P.B101',
    headPerson: 'KTV Nguyễn Văn Bảo',
    status: 'ACTIVE',
  },
  {
    id: 'room-08',
    unitType: 'ROOM',
    code: 'XN-SH',
    name: 'Phòng Xét nghiệm Sinh hóa',
    departmentId: 'dept-03',
    departmentName: 'Khoa Xét nghiệm',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 1 - P.B102',
    headPerson: 'KTV Nguyễn Văn Bảo',
    status: 'ACTIVE',
  },
  {
    id: 'room-09',
    unitType: 'ROOM',
    code: 'XN-VS',
    name: 'Phòng Xét nghiệm Vi sinh',
    departmentId: 'dept-03',
    departmentName: 'Khoa Xét nghiệm',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 1 - P.B103',
    headPerson: 'KTV Nguyễn Văn Bảo',
    status: 'ACTIVE',
  },
  {
    id: 'room-10',
    unitType: 'ROOM',
    code: 'CDHA-SA-1',
    name: 'Phòng Siêu âm 1',
    departmentId: 'dept-04',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 2 - P.B201',
    headPerson: 'KTV Trần Thị Cúc',
    status: 'ACTIVE',
  },
  {
    id: 'room-11',
    unitType: 'ROOM',
    code: 'CDHA-SA-2',
    name: 'Phòng Siêu âm 2',
    departmentId: 'dept-04',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 2 - P.B202',
    headPerson: 'KTV Trần Thị Cúc',
    status: 'ACTIVE',
  },
  {
    id: 'room-12',
    unitType: 'ROOM',
    code: 'CDHA-XQ',
    name: 'Phòng X-quang',
    departmentId: 'dept-04',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 2 - P.B203',
    headPerson: 'KTV Trần Thị Cúc',
    status: 'ACTIVE',
  },
  {
    id: 'room-13',
    unitType: 'ROOM',
    code: 'CDHA-CT',
    name: 'Phòng CT',
    departmentId: 'dept-04',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 2 - P.B204',
    headPerson: 'KTV Lê Thị Đào',
    status: 'ACTIVE',
  },
  {
    id: 'room-14',
    unitType: 'ROOM',
    code: 'CDHA-MRI',
    name: 'Phòng MRI',
    departmentId: 'dept-04',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 2 - P.B205',
    headPerson: 'KTV Lê Thị Đào',
    status: 'ACTIVE',
  },
  {
    id: 'room-15',
    unitType: 'ROOM',
    code: 'TDCN-NS',
    name: 'Phòng Nội soi',
    departmentId: 'dept-05',
    departmentName: 'Khoa Thăm dò chức năng',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 3 - P.B301',
    headPerson: 'KTV Lê Thị Đào',
    status: 'ACTIVE',
  },
  {
    id: 'room-16',
    unitType: 'ROOM',
    code: 'TDCN-DT',
    name: 'Phòng Điện tim',
    departmentId: 'dept-05',
    departmentName: 'Khoa Thăm dò chức năng',
    type: 'PARACLINICAL',
    location: 'Tòa B - Tầng 3 - P.B302',
    headPerson: 'KTV Lê Thị Đào',
    status: 'ACTIVE',
  },
];

export const departmentApi = {
  async getItems() {
    try {
      const data = await apiClient.request('/departments/items');
      if (Array.isArray(data) && data.length > 0) {
        return data;
      }
      return INITIAL_DEPARTMENT_ITEMS;
    } catch {
      return INITIAL_DEPARTMENT_ITEMS;
    }
  },

  async getStats() {
    try {
      return await apiClient.request('/departments/stats');
    } catch {
      const totalDepts = INITIAL_DEPARTMENT_ITEMS.filter((i) => i.unitType === 'DEPARTMENT').length;
      const roomItems = INITIAL_DEPARTMENT_ITEMS.filter((i) => i.unitType === 'ROOM');
      const totalRooms = roomItems.length;
      const activeRooms = roomItems.filter((i) => i.status === 'ACTIVE').length;
      const maintenanceRooms = totalRooms - activeRooms;
      const activeRate = totalRooms > 0 ? Number(((activeRooms / totalRooms) * 100).toFixed(1)) : 0;
      const maintenanceNote = maintenanceRooms > 0
        ? `${maintenanceRooms} phòng đang kiểm tra thiết bị`
        : 'Tất cả phòng hoạt động bình thường';

      return {
        totalDepartments: totalDepts,
        totalRooms: totalRooms,
        activeRooms: activeRooms,
        maintenanceRooms: maintenanceRooms,
        activeRate: activeRate,
        maintenanceNote: maintenanceNote,
        activeCount: activeRooms,
        maintenanceCount: maintenanceRooms,
      };
    }
  },

  async createDepartment(data) {
    return await apiClient.request('/departments', {
      method: 'POST',
      body: data,
    });
  },

  async updateDepartment(id, data) {
    return await apiClient.request(`/departments/${id}`, {
      method: 'PUT',
      body: data,
    });
  },

  async toggleDepartmentStatus(id) {
    return await apiClient.request(`/departments/${id}/status`, {
      method: 'PATCH',
    });
  },

  async createRoom(data) {
    return await apiClient.request('/departments/rooms', {
      method: 'POST',
      body: data,
    });
  },

  async updateRoom(id, data) {
    return await apiClient.request(`/departments/rooms/${id}`, {
      method: 'PUT',
      body: data,
    });
  },

  async toggleRoomStatus(id) {
    return await apiClient.request(`/departments/rooms/${id}/status`, {
      method: 'PATCH',
    });
  },
};

export default departmentApi;
