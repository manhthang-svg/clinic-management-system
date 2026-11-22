import { apiClient } from '../services/session';

/**
 * API client for UC-78: View Audit Log (Admin).
 */
export const auditLogApi = {
  /**
   * @param {Object} params - { keyword, action, entityType, outcome, from, to, page, size }
   */
  async search({ keyword, action, entityType, outcome, from, to, page = 0, size = 20 } = {}, signal) {
    const params = new URLSearchParams();
    if (keyword?.trim()) params.append('keyword', keyword.trim());
    if (action) params.append('action', action);
    if (entityType) params.append('entityType', entityType);
    if (outcome) params.append('outcome', outcome);
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    params.append('page', page);
    params.append('size', size);
    return apiClient.request(`/audit-logs?${params.toString()}`, { signal });
  },

  async getDetail(id, signal) {
    return apiClient.request(`/audit-logs/${encodeURIComponent(id)}`, { signal });
  },

  async listEntityTypes(signal) {
    return apiClient.request('/audit-logs/entity-types', { signal });
  },
};

export default auditLogApi;
