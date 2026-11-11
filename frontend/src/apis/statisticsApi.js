import { apiClient } from '../services/session';

/** UC-26 View Statistics Dashboard (GET /statistics/dashboard). Dates are YYYY-MM-DD, both inclusive. */
export function createStatisticsApi(client = apiClient) {
  return {
    getDashboard: ({ from, to } = {}, signal) => {
      const params = new URLSearchParams();
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      const query = params.toString();
      return client.request(`/statistics/dashboard${query ? `?${query}` : ''}`, { signal });
    },
  };
}

export const statisticsApi = createStatisticsApi();

export default statisticsApi;
