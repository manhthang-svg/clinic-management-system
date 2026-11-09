/**
 * Dữ liệu danh mục phòng máy và xuất hằng số phục vụ module Kỹ thuật viên
 */

export { REJECT_REASONS } from './technicianConstants';

// Danh sách phòng tạm thời cho màn hình Nhập kết quả (sẽ được thay thế khi kết nối API Màn 2)
export const TECHNICIAN_ROOM_LIST = [
  {
    id: 'ROOM_US_XRAY_01',
    code: 'P.105',
    name: 'Phòng Siêu âm & X-quang 01',
    location: 'Khu B - Tầng 1',
    description: 'Chụp X-quang kỹ thuật số DR và Siêu âm tổng quát',
    activeTech: 'KTV. Lê Thị Mai',
  },
  {
    id: 'ROOM_LAB_01',
    code: 'P.102',
    name: 'Phòng Xét nghiệm Huyết học & Sinh hóa 01',
    location: 'Khu A - Tầng 1',
    description: 'Xét nghiệm huyết học tự động, sinh hóa máu, nước tiểu',
    activeTech: 'KTV. Nguyễn Minh Tuấn',
  },
  {
    id: 'ROOM_US_02',
    code: 'P.106',
    name: 'Phòng Siêu âm màu Doppler 4D 02',
    location: 'Khu B - Tầng 1',
    description: 'Siêu âm tim Doppler, mạch máu và sản phụ khoa 4D',
    activeTech: 'KTV. Trần Bảo Long',
  },
  {
    id: 'ROOM_ECG_01',
    code: 'P.108',
    name: 'Phòng Điện tim & Thăm dò chức năng 01',
    location: 'Khu C - Tầng 2',
    description: 'Điện tâm đồ (ECG) 12 đạo trình, Đo chức năng hô hấp',
    activeTech: 'KTV. Phạm Thu Hà',
  },
];
