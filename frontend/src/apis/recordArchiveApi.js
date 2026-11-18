import { apiClient } from '../services/session';

/**
 * API client for UC-79: View Record Archive (Admin EMR).
 */
export const recordArchiveApi = {
  /**
   * Tìm kiếm danh sách hồ sơ lưu trữ EMR (UC-79)
   * @param {Object} params - { keyword, category, from, to, page, size }
   */
  async searchArchives({ keyword, category, from, to, page = 0, size = 20 } = {}, signal) {
    const params = new URLSearchParams();
    if (keyword) params.append('keyword', keyword.trim());
    if (category) params.append('category', category);
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    params.append('page', page);
    params.append('size', size);

    const query = params.toString();
    const url = `/record-archives${query ? `?${query}` : ''}`;
    return apiClient.request(url, { signal });
  },
};

export default recordArchiveApi;

