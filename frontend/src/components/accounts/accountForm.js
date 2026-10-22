// Form pieces and validation shared by UC-4, UC-7 and UC-8 (rules: docs/use-cases/staff-account.md#attributes).
import { normalizeSearch } from './accountFormat';

export const EMPTY_ACCOUNT_FORM = {
  username: '',
  fullName: '',
  title: '',
  dateOfBirth: '',
  gender: '',
  phone: '',
  email: '',
  address: '',
  role: 'DOCTOR',
};

const USERNAME_PATTERN = /^[a-z0-9._-]{3,50}$/;
const FULL_NAME_PATTERN = /^[\p{L} ]+$/u;
const PHONE_PATTERN = /^0\d{9}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function accountToForm(account) {
  return {
    username: account.username || '',
    fullName: account.fullName || '',
    title: account.title || '',
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
      : !USERNAME_PATTERN.test(username) ? 'Tên đăng nhập 3–50 ký tự, chỉ gồm a–z, 0–9 và . _ -'
        : isUsernameTaken(username, accounts) ? 'Tên đăng nhập đã tồn tại.' : null);

  const fullName = form.fullName.trim();
  check('fullName',
    !fullName ? 'Vui lòng nhập họ và tên.'
      : fullName.length > 100 ? 'Họ và tên tối đa 100 ký tự.'
        : !FULL_NAME_PATTERN.test(fullName) ? 'Họ và tên chỉ gồm chữ cái và khoảng trắng.' : null);

  check('title', form.title.trim().length > 50 ? 'Học hàm / học vị tối đa 50 ký tự.' : null);
  check('dateOfBirth', form.dateOfBirth && new Date(form.dateOfBirth) >= new Date() ? 'Ngày sinh phải ở trong quá khứ.' : null);
  check('address', form.address.trim().length > 255 ? 'Địa chỉ tối đa 255 ký tự.' : null);

  const phone = form.phone.trim();
  check('phone',
    !phone ? 'Vui lòng nhập số điện thoại.'
      : !PHONE_PATTERN.test(phone) ? 'Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.' : null);

  const email = form.email.trim();
  check('email',
    !email ? 'Vui lòng nhập email.'
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
    dateOfBirth: form.dateOfBirth || undefined,
    gender: form.gender || undefined,
    phone: form.phone.trim(),
    email: form.email.trim(),
    address: form.address.trim() || undefined,
  };
}
