import { apiClient } from '../services/session';

// ghep tham so vao duong dan
function withQuery(path, values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

// API thong ke admin (ngay YYYY-MM-DD, tinh ca 2 dau)
export function createStatisticsApi(client = apiClient) {
  return {
    // UC-26: tong quan
    getDashboard: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/dashboard', { from, to }), { signal }),

    // UC-27: luot kham (theo ngay / thang)
    getVisits: ({ from, to, groupBy } = {}, signal) =>
      client.request(withQuery('/statistics/visits', { from, to, groupBy }), { signal }),

    // UC-28: theo bac si
    getDoctors: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/doctors', { from, to }), { signal }),

    // UC-29: theo khoa / phong
    getDepartments: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/departments', { from, to }), { signal }),

    // UC-30: dich vu CLS
    getParaclinical: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/paraclinical', { from, to }), { signal }),

    // UC-31: doanh thu (theo ngay / thang)
    getRevenue: ({ from, to, groupBy } = {}, signal) =>
      client.request(withQuery('/statistics/revenue', { from, to, groupBy }), { signal }),

    // UC-32: benh thuong gap (top 1 - 100)
    getDiagnoses: ({ from, to, limit } = {}, signal) =>
      client.request(withQuery('/statistics/diagnoses', { from, to, limit }), { signal }),

    // UC-33: tai file Excel cac bao cao da chon (khong chon = tat ca)
    exportExcel: ({ from, to, reports = [], groupBy, limit } = {}) =>
      client.download(withQuery('/statistics/export', {
        from, to, reports: reports.join(','), groupBy, limit,
      })),
  };
}

export const statisticsApi = createStatisticsApi();

export default statisticsApi;
