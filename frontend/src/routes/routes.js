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
    REPORTS_DOCTORS: '/admin/reports/doctors',
    REPORTS_DEPARTMENTS: '/admin/reports/departments',
    REPORTS_PARACLINICAL: '/admin/reports/paraclinical',
    REPORTS_REVENUE: '/admin/reports/revenue',
    REPORTS_DIAGNOSES: '/admin/reports/diagnoses',
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
    EXAMINED_PATIENTS: '/doctor/examined-patients',
    MEDICAL_RECORDS: '/doctor/medical-records',
    MEDICAL_RECORD_DETAIL: '/doctor/medical-records/:id',
    MEDICAL_RECORD_VISIT_DETAIL: '/doctor/medical-records/visits/:visitId',
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
export const EXAMINATION_SECTIONS = ['clinical', 'paraclinical', 'orders', 'conclusion', 'prescription', 'transfer'];

/** Link of one visit's examination screen, e.g. /doctor/clinical-examination/{visitId}/paraclinical. */
export function clinicalExaminationPath(visitId, section = 'clinical') {
  const base = `${ROUTES.DOCTOR.CLINICAL_EXAMINATION}/${encodeURIComponent(visitId)}`;
  return section === 'clinical' ? base : `${base}/${section}`;
}

/** Link of one room's waiting list (UC-42 step 6), e.g. /receptionist/room-queue/{roomId}. */
export function roomQueuePath(roomId) {
  return `${ROUTES.RECEPTIONIST.ROOM_QUEUE}/${encodeURIComponent(roomId)}`;
}

export default ROUTES;
