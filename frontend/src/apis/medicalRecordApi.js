import { apiClient } from '../services/session';

/**
 * API hồ sơ bệnh án: UC-51, UC-52, UC-53 (docs/uc-51-52-medical-record-view.md).
 * Tuyệt đối không mock hay fallback, kết nối dữ liệu thật từ Backend.
 */
export const medicalRecordApi = {
  /**
   * Cấp 1: Tìm kiếm danh sách bệnh nhân khớp bộ lọc
   */
  async searchPatients({ from, to, status, roomId, icd10, keyword, page = 0, size = 20 } = {}, signal) {
    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (status && status !== 'ALL') params.set('status', status);
    if (roomId && roomId !== 'ALL') params.set('roomId', roomId);
    if (icd10) params.set('icd10', icd10.trim());
    if (keyword) params.set('keyword', keyword.trim());
    params.set('page', String(page));
    params.set('size', String(size));

    const query = params.toString();
    return apiClient.request(`/medical-records/patients${query ? `?${query}` : ''}`, { signal });
  },

  /**
   * Cấp 2 / AF-01: Lấy danh sách lượt khám của một bệnh nhân được chọn
   */
  async getPatientVisits(patientId, signal) {
    return apiClient.request(`/medical-records/patients/${encodeURIComponent(patientId)}/visits`, { signal });
  },

  /**
   * UC-52: Xem chi tiết hồ sơ bệnh án theo ID
   */
  async getMedicalRecordDetail(id, signal) {
    return apiClient.request(`/medical-records/${encodeURIComponent(id)}`, { signal });
  },

  /**
   * UC-52: Xem chi tiết hồ sơ bệnh án theo visitId
   */
  async getMedicalRecordByVisitId(visitId, signal) {
    return apiClient.request(`/medical-records/by-visit/${encodeURIComponent(visitId)}`, { signal });
  },

  /**
   * Danh sách phòng khám lâm sàng phục vụ dropdown bộ lọc
   */
  async getActiveRooms(signal) {
    return apiClient.request('/rooms/active-examination', { signal });
  },

  /**
   * Gợi ý tìm kiếm mã hoặc tên bệnh ICD-10
   */
  async searchIcd10(keyword, signal) {
    const params = new URLSearchParams({ keyword, limit: '10' });
    return apiClient.request(`/clinical-examinations/icd10?${params.toString()}`, { signal });
  },

  /**
   * UC-53: xuất hồ sơ bệnh án đã đóng ra PDF theo mẫu 15/BV1. Mục đích và người nhận đi trong body, không đưa
   * vào URL. Trả về { blob, fileName }.
   */
  async exportPdf(id, { purpose, recipient }, signal) {
    return apiClient.download(`/medical-records/${encodeURIComponent(id)}/commands/export-pdf`, {
      method: 'POST',
      body: { purpose, recipient: recipient || null },
      signal,
    });
  },

  /**
   * UC-52 bước 4 (BR-52-06): tải một tệp kết quả CLS của hồ sơ. Backend kiểm tra quyền và ghi audit mỗi lần tải.
   * Trả về { blob, fileName }.
   */
  async downloadResultFile(medicalRecordId, fileId, signal) {
    return apiClient.download(
      `/medical-records/${encodeURIComponent(medicalRecordId)}/result-files/${encodeURIComponent(fileId)}`,
      { signal },
    );
  },
};

export default medicalRecordApi;

