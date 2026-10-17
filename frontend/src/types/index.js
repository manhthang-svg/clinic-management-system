/**
 * @typedef {Object} Account
 * @property {string} id
 * @property {string} code
 * @property {string} username
 * @property {string} fullName
 * @property {string} email
 * @property {string} phone
 * @property {'DOCTOR' | 'RECEPTIONIST' | 'CASHIER' | 'TECHNICIAN' | 'ADMIN'} role
 * @property {string} department
 * @property {'ACTIVE' | 'LOCKED'} status
 * @property {string} createdAt
 */

/**
 * @typedef {Object} Session
 * @property {'restoring' | 'anonymous' | 'authenticated'} status
 * @property {Account} [account]
 * @property {boolean} [passwordChangeRequired]
 */

export const USER_ROLES = {
  ADMIN: 'ADMIN',
  RECEPTIONIST: 'RECEPTIONIST',
  DOCTOR: 'DOCTOR',
  TECHNICIAN: 'TECHNICIAN',
  CASHIER: 'CASHIER',
};
