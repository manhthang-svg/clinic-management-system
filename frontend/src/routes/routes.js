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
    PROFILE: '/admin/profile',
    PATIENTS: '/admin/patients',
    APPOINTMENTS: '/admin/appointments',
    EXAMINATIONS: '/admin/examinations',
    SUBCLINICAL: '/admin/subclinical',
    BILLING: '/admin/billing',
    STAFF: '/admin/staff',
    REPORTS: '/admin/reports',
    SETTINGS: '/admin/settings',
  },
};

export default ROUTES;