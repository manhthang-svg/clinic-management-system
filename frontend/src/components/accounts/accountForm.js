// Form pieces and validation shared by UC-4, UC-7 and UC-8 (rules: docs/use-cases/staff-account.md#attributes).
import { normalizeSearch } from './accountFormat';

export const EMPTY_ACCOUNT_FORM = {
  username: '',
  fullName: '',
  title: '',
  practiceLicenseNo: '',
  maLienthongBacsi: '',
  citizenId: '',
  dateOfBirth: '',
  gender: '',
  phone: '',
  email: '',
  address: '',
  role: 'DOCTOR',
};

// Same rules as the backend (StaffAccount.USERNAME_FORMAT and the request DTO patterns).
const USERNAME_PATTERN = /^[a-z0-9._]{4,30}$/;
// BR-59: letters (including Vietnamese), spaces, apostrophes and hyphens; 2–120 characters after normalization.
const FULL_NAME_PATTERN = /^[\p{L}\p{M} '-]+$/u;
const FULL_NAME_MIN = 2;
// BR-62: academic title has letters, spaces, dots and commas, at most 50 characters.
// BR-64: practice licence has 5–50 letters, digits, slashes or hyphens (the backend checks uniqueness).
export const PRACTICE_LICENSE_PATTERN = /^[\p{L}\p{N}/-]{5,50}$/u;
// BR-49, BR-65: prescriber link code from the national prescription system, letters and digits only (V55).
export const MA_LIENTHONG_BACSI_PATTERN = /^[A-Za-z0-9]+$/;
const MA_LIENTHONG_BACSI_MAX = 50;
// BR-59-16: personal identification number of a Doctor, 12 digits (same rule as the backend).
export const CITIZEN_ID_PATTERN = /^[0-9]{12}$/;
export const ACADEMIC_TITLE_PATTERN = /^[\p{L}\p{M} .,]+$/u;
const FULL_NAME_MAX = 120;
const PHONE_PATTERN = /^(?:03[2-9]|05[2689]|07[06789]|08[1-689]|09[0-46-9])\d{7}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const UNDERAGE_MESSAGE = 'Ngày sinh không hợp lệ. Nhân viên phải đủ 18 tuổi.';

/** Today's date (yyyy-mm-dd) in the clinic's time zone. */
function clinicToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
}

/**
 * Latest date of birth of a staff member who is at least 18 on `today` (yyyy-mm-dd), as the backend computes it
 * with LocalDate.minusYears(18): 29 February falls back to 28 February in a non-leap year.
 */
export function latestAdultBirthDate(today) {
  const [year, month, day] = today.split('-').map(Number);
  const target = year - 18;
  const lastDay = new Date(Date.UTC(target, month, 0)).getUTCDate();
  const pad = (n) => String(n).padStart(2, '0');
  return `${target}-${pad(month)}-${pad(Math.min(day, lastDay))}`;
}

/** BR-59: trims the full name and collapses repeated spaces, as the backend stores it. */
export function normalizeFullName(value) {
  return value.trim().replace(/\s+/g, ' ');
}

export function accountToForm(account) {
  return {
    username: account.username || '',
    fullName: account.fullName || '',
    title: account.title || '',
    practiceLicenseNo: account.practiceLicenseNo || '',
    maLienthongBacsi: account.maLienthongBacsi || '',
    citizenId: account.citizenId || '',
    dateOfBirth: account.dateOfBirth || '',
    gender: account.gender || '',
    phone: account.phone || '',
    email: account.email || '',
    address: account.address || '',
    role: account.role,
  };
}

const msg02 = (field) => `${field} là bắt buộc.`;
const msg05 = (field) => `${field} không đúng định dạng.`;
const msg07 = (field) => `${field} đã tồn tại trong hệ thống.`;
const msg08 = (max) => `Vượt quá độ dài tối đa ${max} ký tự.`;

function isEmailTaken(email, accounts, exceptId) {
  const value = normalizeSearch(email);
  return value !== '' && accounts.some((a) => a.id !== exceptId && normalizeSearch(a.email || '') === value);
}

function isUsernameTaken(username, accounts) {
  const value = username.trim().toLowerCase();
  return value !== '' && accounts.some((a) => a.username === value);
}

/**
 * Returns { field: message } for the given fields. Client checks give feedback only; the backend decides.
 * `fields` limits validation to what the screen edits (UC-4 edits only phone and email).
 */
export function validateAccountForm(form, { accounts = [], exceptId, fields, today = clinicToday() }) {
  const errors = {};
  const check = (field, message) => {
    if ((!fields || fields.includes(field)) && message) errors[field] = message;
  };

  // Messages follow the SRS templates: MSG02 required, MSG05 format, MSG07 duplicate, MSG08 too long.
  const username = form.username.trim().toLowerCase();
  check('username',
    !username ? msg02('Tên đăng nhập')
      : !USERNAME_PATTERN.test(username) ? msg05('Tên đăng nhập')
        : isUsernameTaken(username, accounts) ? msg07('Tên đăng nhập') : null);

  const fullName = normalizeFullName(form.fullName);
  const fullNameLength = [...fullName].length;
  check('fullName',
    !fullName ? msg02('Họ và tên')
      : fullNameLength > FULL_NAME_MAX ? msg08(FULL_NAME_MAX)
        : fullNameLength < FULL_NAME_MIN || !FULL_NAME_PATTERN.test(fullName) ? msg05('Họ và tên') : null);

  const needsAcademicTitle = form.role === 'DOCTOR' || form.role === 'TECHNICIAN';
  check('title', needsAcademicTitle && !form.title.trim()
    ? msg02('Học hàm / học vị')
    : form.title.trim().length > 50 ? msg08(50)
      : form.title.trim() && !ACADEMIC_TITLE_PATTERN.test(form.title.trim()) ? msg05('Học hàm / học vị') : null);
  check('practiceLicenseNo', needsAcademicTitle && !form.practiceLicenseNo.trim()
    ? msg02('Số giấy phép hành nghề')
    : form.practiceLicenseNo.trim() && !PRACTICE_LICENSE_PATTERN.test(form.practiceLicenseNo.trim())
      ? msg05('Số giấy phép hành nghề') : null);
  // Optional and only for a Doctor; the backend checks uniqueness.
  const maLienthongBacsi = (form.maLienthongBacsi || '').trim();
  check('maLienthongBacsi', !maLienthongBacsi || form.role !== 'DOCTOR' ? null
    : maLienthongBacsi.length > MA_LIENTHONG_BACSI_MAX ? msg08(MA_LIENTHONG_BACSI_MAX)
      : !MA_LIENTHONG_BACSI_PATTERN.test(maLienthongBacsi) ? msg05('Mã liên thông bác sĩ') : null);
  // Optional and only for a Doctor; signing certificates are matched against it. The backend checks uniqueness.
  const citizenId = (form.citizenId || '').trim();
  check('citizenId', !citizenId || form.role !== 'DOCTOR' || CITIZEN_ID_PATTERN.test(citizenId) ? null
    : msg05('Số định danh cá nhân'));
  // BR-62: staff must be at least 18 years old (the backend applies the same rule).
  check('dateOfBirth', form.dateOfBirth && form.dateOfBirth > latestAdultBirthDate(today) ? UNDERAGE_MESSAGE : null);
  check('address', form.address.trim().length > 255 ? msg08(255) : null);
  const phone = form.phone.trim();
  check('phone',
    !phone ? msg02('Số điện thoại')
      : !PHONE_PATTERN.test(phone) ? msg05('Số điện thoại') : null);

  const email = form.email.trim();
  // Email is optional (UC-04, UC-07, UC-08); validate only when entered.
  check('email',
    !email ? null
      : email.length > 100 ? msg08(100)
        : !EMAIL_PATTERN.test(email) ? msg05('Email')
          : isEmailTaken(email, accounts, exceptId) ? msg07('Email') : null);

  return errors;
}

/** Trimmed values in the shape the account list stores. */
export function formToAccountFields(form) {
  return {
    fullName: normalizeFullName(form.fullName),
    title: form.title.trim() || undefined,
    practiceLicenseNo: form.practiceLicenseNo.trim() || undefined,
    dateOfBirth: form.dateOfBirth || undefined,
    gender: form.gender || undefined,
    phone: form.phone.trim(),
    email: form.email.trim(),
    address: form.address.trim() || undefined,
  };
}
