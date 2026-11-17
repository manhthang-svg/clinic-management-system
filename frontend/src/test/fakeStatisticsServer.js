import { vi } from 'vitest';

// cat 1 trang nhu backend (PageResponse)
export function pageOf(rows, { page = 0, size = 20 } = {}) {
  return {
    content: rows.slice(page * size, (page + 1) * size),
    page: { number: page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) },
  };
}

function fold(text) {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
}

const matches = (search, ...values) => !search || values.some((value) => fold(value).includes(fold(search)));

/**
 * API thong ke gia cho test: nhan du lieu day du nhu API cu tra ve, roi lam viec cua backend
 * (so lieu tong cho the / bieu do, loc + cat trang cho bang). Moi ham la vi.fn de kiem tra loi goi.
 */
// du lieu co the la ham theo tham so (vd theo groupBy ngay / thang)
const valueOf = (data, params) => (typeof data === 'function' ? data(params ?? {}) : data);

export function fakeStatisticsServer({ visits, revenue, doctors, departments, paraclinical, diagnoses } = {}) {
  return {
    getVisits: vi.fn(async (params) => valueOf(visits, params)),
    getVisitRows: vi.fn(async (params) => pageOf(
      valueOf(visits, params).series.filter((row) => row.counts.total > 0), params)),

    getRevenue: vi.fn(async (params) => valueOf(revenue, params)),
    getRevenueRows: vi.fn(async (params) => pageOf(
      valueOf(revenue, params).series.filter((row) => row.amounts.payments > 0), params)),

    getDoctorOverview: vi.fn(async () => {
      const working = doctors.doctors.filter((doctor) => doctor.visits > 0);
      return {
        from: doctors.from,
        to: doctors.to,
        examinedVisits: doctors.examinedVisits,
        visitsWithoutDoctor: doctors.visitsWithoutDoctor,
        doctorCount: doctors.doctors.length,
        workingDoctors: working.length,
        topDoctors: working.slice(0, 10),
      };
    }),
    getDoctorRows: vi.fn(async (params) => pageOf(
      doctors.doctors.filter((doctor) => matches(params.search, doctor.doctorName)), params)),

    getDepartmentOverview: vi.fn(async () => {
      const busy = departments.departments.filter((d) => d.visits + d.services > 0);
      const rooms = departments.departments.flatMap((d) => d.rooms);
      return {
        from: departments.from,
        to: departments.to,
        visits: departments.visits,
        services: departments.services,
        departmentCount: departments.departments.length,
        busyDepartments: busy.length,
        busyRooms: rooms.filter((room) => room.visits + room.services > 0).length,
        roomCount: rooms.length,
        busy: busy.map((d) => ({ departmentName: d.departmentName, visits: d.visits, services: d.services })),
      };
    }),
    getDepartmentRows: vi.fn(async (params) => pageOf(
      departments.departments.filter((d) => !params.type || params.type === 'ALL' || d.type === params.type), params)),

    getParaclinicalOverview: vi.fn(async () => ({
      from: paraclinical.from,
      to: paraclinical.to,
      performed: paraclinical.performed,
      withResult: paraclinical.withResult,
      byType: paraclinical.byType,
      byGroup: paraclinical.byGroup,
      serviceCount: paraclinical.byService.length,
      roomCount: paraclinical.byRoom.length,
      topRooms: paraclinical.byRoom.slice(0, 10),
    })),
    getParaclinicalServices: vi.fn(async (params) => pageOf(
      paraclinical.byService.filter((s) => matches(params.search, s.serviceName, s.serviceCode)), params)),

    getDiagnosisOverview: vi.fn(async () => ({
      from: diagnoses.from,
      to: diagnoses.to,
      limit: diagnoses.limit,
      conclusions: diagnoses.conclusions,
      withIcd: diagnoses.withIcd,
      withoutIcd: diagnoses.withoutIcd,
      distinctCodes: diagnoses.distinctCodes,
      topDiagnoses: diagnoses.mainDiagnoses.slice(0, 10),
    })),
    getDiagnosisRows: vi.fn(async (params) => {
      const lists = {
        main: diagnoses.mainDiagnoses,
        categories: diagnoses.categories ?? [],
        secondary: diagnoses.secondaryDiagnoses ?? [],
      };
      return pageOf(lists[params.list], params);
    }),
  };
}
