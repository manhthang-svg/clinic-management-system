export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  ADMIN: {
    DASHBOARD: '/admin/dashboard',
    ACCOUNTS: '/admin/accounts',
    ACCOUNT_CREATE: '/admin/accounts/new',
    ACCOUNT_DETAIL: '/admin/accounts/:id',
    ACCOUNT_EDIT: '/admin/accounts/:id/edit',
    SERVICES: '/admin/services',
    SERVICE_CREATE: '/admin/services/new',
    PROFILE: '/admin/profile',
    PATIENTS: '/admin/patients',
    APPOINTMENTS: '/admin/appointments',
    EXAMINATIONS: '/admin/examinations',
    SUBCLINICAL: '/admin/subclinical',
    DEPARTMENTS: '/admin/departments',
    BILLING: '/admin/billing',
    STAFF: '/admin/staff',
    REPORTS: '/admin/reports',
    SETTINGS: '/admin/settings',
  },
};

export default ROUTES;