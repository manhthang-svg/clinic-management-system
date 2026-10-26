// Form pieces and validation shared by UC-4, UC-7 and UC-8 (rules: docs/use-cases/staff-account.md#attributes).
import { normalizeSearch } from './accountFormat';

export const EMPTY_ACCOUNT_FORM = {
  username: '',
  fullName: '',
  title: '',
  practiceLicenseNo: '',
  dateOfBirth: '',
  gender: '',
  phone: '',
  email: '',
  address: '',
  role: 'DOCTOR',
};

// Same rules as the backend (StaffAccount.USERNAME_FORMAT and the request DTO patterns).
const USERNAME_PATTERN = /^[a-z0-9._]{4,30}$/;
const FULL_NAME_PATTERN = /^[\p{L} ]+$/u;
const PHONE_PATTERN = /^(?:03[2-9]|05[2689]|07[06789]|08[1-689]|09[0-46-9])\d{7}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function accountToForm(account) {
  return {
    username: account.username || '',
    fullName: account.fullName || '',
    title: account.title || '',
    practiceLicenseNo: account.practiceLicenseNo || '',
    dateOfBirth: account.dateOfBirth || '',
    gender: account.gender || '',
    phone: account.phone || '',
    email: account.email || '',
    address: account.address || '',
    role: account.role,
  };
}

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
export function validateAccountForm(form, { accounts = [], exceptId, fields }) {
  const errors = {};
  const check = (field, message) => {
    if ((!fields || fields.includes(field)) && message) errors[field] = message;
  };

  const username = form.username.trim().toLowerCase();
  check('username',
    !username ? 'Vui lòng nhập tên đăng nhập.'
      : !USERNAME_PATTERN.test(username) ? 'Tên đăng nhập 4–30 ký tự, chỉ gồm a–z, 0–9, dấu chấm và gạch dưới.'
        : isUsernameTaken(username, accounts) ? 'Tên đăng nhập đã tồn tại.' : null);

  const fullName = form.fullName.trim();
  check('fullName',
    !fullName ? 'Vui lòng nhập họ và tên.'
      : fullName.length > 100 ? 'Họ và tên tối đa 100 ký tự.'
        : !FULL_NAME_PATTERN.test(fullName) ? 'Họ và tên chỉ gồm chữ cái và khoảng trắng.' : null);

  const needsAcademicTitle = form.role === 'DOCTOR' || form.role === 'TECHNICIAN';
  check('title', needsAcademicTitle && !form.title.trim()
    ? 'Vui lòng nhập học hàm / học vị cho Bác sĩ hoặc Kỹ thuật viên.'
    : form.title.trim().length > 50 ? 'Học hàm / học vị tối đa 50 ký tự.' : null);
  check('practiceLicenseNo', needsAcademicTitle && !form.practiceLicenseNo.trim()
    ? 'Vui lòng nhập số giấy phép hành nghề cho Bác sĩ hoặc Kỹ thuật viên.'
    : form.practiceLicenseNo.trim().length > 255 ? 'Số giấy phép hành nghề tối đa 255 ký tự.' : null);
  check('dateOfBirth', form.dateOfBirth && new Date(form.dateOfBirth) >= new Date() ? 'Ngày sinh phải ở trong quá khứ.' : null);
  check('address', form.address.trim().length > 255 ? 'Địa chỉ tối đa 255 ký tự.' : null);
  const phone = form.phone.trim();
  check('phone',
    !phone ? 'Vui lòng nhập số điện thoại.'
      : !PHONE_PATTERN.test(phone) ? 'Số điện thoại gồm 10 chữ số, bắt đầu bằng 0 và đúng đầu số di động Việt Nam.' : null);

  const email = form.email.trim();
  // Email is optional (UC-04, UC-07, UC-08); validate only when entered.
  check('email',
    !email ? null
      : email.length > 100 ? 'Email tối đa 100 ký tự.'
        : !EMAIL_PATTERN.test(email) ? 'Email không đúng định dạng.'
          : isEmailTaken(email, accounts, exceptId) ? 'Email đã được dùng cho tài khoản khác.' : null);

  return errors;
}

/** Trimmed values in the shape the account list stores. */
export function formToAccountFields(form) {
  return {
    fullName: form.fullName.trim(),
    title: form.title.trim() || undefined,
    practiceLicenseNo: form.practiceLicenseNo.trim() || undefined,
    dateOfBirth: form.dateOfBirth || undefined,
    gender: form.gender || undefined,
    phone: form.phone.trim(),
    email: form.email.trim(),
    address: form.address.trim() || undefined,
  };
}
