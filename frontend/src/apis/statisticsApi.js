import { apiClient } from '../services/session';

function withQuery(path, values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

/** Admin statistics endpoints. Dates are YYYY-MM-DD, both inclusive. */
export function createStatisticsApi(client = apiClient) {
  return {
    /** UC-26 View Statistics Dashboard. */
    getDashboard: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/dashboard', { from, to }), { signal }),

    /** UC-27 View Patient Visit Statistics; groupBy is DAY or MONTH. */
    getVisits: ({ from, to, groupBy } = {}, signal) =>
      client.request(withQuery('/statistics/visits', { from, to, groupBy }), { signal }),

    /** UC-28 View Doctor Statistics. */
    getDoctors: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/doctors', { from, to }), { signal }),

    /** UC-29 View Department and Room Statistics. */
    getDepartments: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/departments', { from, to }), { signal }),

    /** UC-30 View Paraclinical Service Statistics. */
    getParaclinical: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/paraclinical', { from, to }), { signal }),

    /** UC-31 View Revenue Statistics; groupBy is DAY or MONTH. */
    getRevenue: ({ from, to, groupBy } = {}, signal) =>
      client.request(withQuery('/statistics/revenue', { from, to, groupBy }), { signal }),
  };
}

export const statisticsApi = createStatisticsApi();

export default statisticsApi;
