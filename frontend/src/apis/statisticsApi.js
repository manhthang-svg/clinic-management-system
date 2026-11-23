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

    // ---- bang thong ke: loc + phan trang o server (page tu 0, size mac dinh 20) ----
    // UC-27: bang chi tiet luot kham
    getVisitRows: ({ from, to, groupBy, page, size } = {}, signal) =>
      client.request(withQuery('/statistics/visits/rows', { from, to, groupBy, page, size }), { signal }),

    // UC-28: so lieu tong + bieu do; bang bac si (tim theo ten)
    getDoctorOverview: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/doctors/overview', { from, to }), { signal }),
    getDoctorRows: ({ from, to, search, page, size } = {}, signal) =>
      client.request(withQuery('/statistics/doctors/rows', { from, to, search, page, size }), { signal }),

    // UC-29: so lieu tong + bieu do; bang khoa (loc loai khoa)
    getDepartmentOverview: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/departments/overview', { from, to }), { signal }),
    getDepartmentRows: ({ from, to, type, page, size } = {}, signal) =>
      client.request(withQuery('/statistics/departments/rows', {
        from, to, type: type === 'ALL' ? undefined : type, page, size,
      }), { signal }),

    // UC-30: so lieu tong + bieu do; bang dich vu (tim theo ten / ma)
    getParaclinicalOverview: ({ from, to } = {}, signal) =>
      client.request(withQuery('/statistics/paraclinical/overview', { from, to }), { signal }),
    getParaclinicalServices: ({ from, to, search, page, size } = {}, signal) =>
      client.request(withQuery('/statistics/paraclinical/services', { from, to, search, page, size }), { signal }),

    // UC-31: bang chi tiet doanh thu
    getRevenueRows: ({ from, to, groupBy, page, size } = {}, signal) =>
      client.request(withQuery('/statistics/revenue/rows', { from, to, groupBy, page, size }), { signal }),

    // UC-32: so lieu tong + bieu do; 3 bang (benh chinh, nhom benh, benh kem theo)
    getDiagnosisOverview: ({ from, to, limit } = {}, signal) =>
      client.request(withQuery('/statistics/diagnoses/overview', { from, to, limit }), { signal }),
    getDiagnosisRows: ({ list, from, to, limit, page, size } = {}, signal) =>
      client.request(withQuery(`/statistics/diagnoses/${list}`, { from, to, limit, page, size }), { signal }),

    // UC-33: tai file Excel cac bao cao da chon (khong chon = tat ca)
    exportExcel: ({ from, to, reports = [], groupBy, limit } = {}) =>
      client.download(withQuery('/statistics/export', {
        from, to, reports: reports.join(','), groupBy, limit,
      })),
  };
}

export const statisticsApi = createStatisticsApi();

export default statisticsApi;
