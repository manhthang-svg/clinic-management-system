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
    ACCOUNT_RESET_PASSWORD: '/admin/accounts/:id/reset-password',
    SERVICES: '/admin/services',
    SERVICE_CREATE: '/admin/services/new',
    SERVICE_DETAIL: '/admin/services/:id',
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
    SCHEDULES: '/technician/schedules',
    QUEUE: '/technician/queue',
  },
};

export default ROUTES;
