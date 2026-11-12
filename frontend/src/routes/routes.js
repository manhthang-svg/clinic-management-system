export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  ADMIN: {
    DASHBOARD: '/admin/dashboard',
    ACCOUNTS: '/admin/accounts',
    ACCOUNT_CREATE: '/admin/accounts/new',
    ACCOUNT_DETAIL: '/admin/accounts/:id',
    ACCOUNT_EDIT: '/admin/accounts/:id/edit',
    ACCOUNT_ASSIGN_ROLE: '/admin/accounts/:id/assign-role',
    SERVICES: '/admin/services',
    SERVICE_CREATE: '/admin/services/new',
    SERVICE_DETAIL: '/admin/services/:id',
    LAB_PARAMETERS: '/admin/lab-parameters',
    MEDICINES: '/admin/medicines',
    MEDICINE_DETAIL: '/admin/medicines/:id',
    PROFILE: '/admin/profile',
    PATIENTS: '/admin/patients',
    APPOINTMENTS: '/admin/appointments',
    EXAMINATIONS: '/admin/examinations',
    SUBCLINICAL: '/admin/subclinical',
    DEPARTMENTS: '/admin/departments',
    SCHEDULES: '/admin/schedules',
    BILLING: '/admin/billing',
    STAFF: '/admin/staff',
    REPORTS: '/admin/reports',
    SETTINGS: '/admin/settings',
    HOSPITAL_INFORMATION: '/admin/hospital-information',
  },

  RECEPTIONIST: {
    OVERVIEW: '/receptionist',
    PROFILE: '/receptionist/profile',
    SCHEDULES: '/receptionist/schedules',
    PATIENTS: '/receptionist/patients',
    PATIENT_CREATE: '/receptionist/patients/new',
    PATIENT_DETAIL: '/receptionist/patients/:id',
    PATIENT_EDIT: '/receptionist/patients/:id/edit',
    VISIT_REGISTER: '/receptionist/patients/:id/register',
    ROOM_QUEUE: '/receptionist/room-queue',
  },

  CASHIER: {
    OVERVIEW: '/cashier',
    SCHEDULES: '/cashier/schedules',
    PAYMENTS: '/cashier/payments',
    PAYMENT_DETAIL: '/cashier/payments/:id',
    PAYMENT_HISTORY: '/cashier/payment-history',
    PROFILE: '/cashier/profile',
  },

  DOCTOR: {
    OVERVIEW: '/doctor/appointments',
    PROFILE: '/doctor/profile',
    SCHEDULES: '/doctor/schedules',
    WAITING_LIST: '/doctor/appointments',
    CLINICAL_EXAMINATION: '/doctor/clinical-examination',
    PARACLINICAL_ORDERS: '/doctor/paraclinical-orders',
    ROOM_TRANSFERS: '/doctor/room-transfers',
    SKIPPED_PATIENTS: '/doctor/skipped-patients',
  },

  TECHNICIAN: {
    OVERVIEW: '/technician',
    DASHBOARD: '/technician/dashboard',
    PROFILE: '/technician/profile',
    SCHEDULES: '/technician/schedules',
    QUEUE: '/technician/queue',
    RESULTS: '/technician/results',
    RESULT_DETAIL: '/technician/results/:orderId',
  },
};

/** Tabs of the examination screen that have their own link; the first is the default. */
export const EXAMINATION_SECTIONS = ['clinical', 'paraclinical', 'orders', 'transfer'];

/** Link of one visit's examination screen, e.g. /doctor/clinical-examination/{visitId}/paraclinical. */
export function clinicalExaminationPath(visitId, section = 'clinical') {
  const base = `${ROUTES.DOCTOR.CLINICAL_EXAMINATION}/${encodeURIComponent(visitId)}`;
  return section === 'clinical' ? base : `${base}/${section}`;
}

export default ROUTES;
