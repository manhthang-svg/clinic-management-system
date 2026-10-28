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
    BILLING: '/admin/billing',
    STAFF: '/admin/staff',
    REPORTS: '/admin/reports',
    SETTINGS: '/admin/settings',
  }, 
  
  CASHIER: {
    OVERVIEW: '/cashier',
    PAYMENTS: '/cashier/payments',
    PAYMENT_DETAIL: '/cashier/payments/:id',
    PAYMENT_HISTORY: '/cashier/payment-history',
    PROFILE: '/cashier/profile',
  },
};

export default ROUTES;
