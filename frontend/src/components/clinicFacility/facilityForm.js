// UC-77 form rules. They mirror UpdateClinicFacilityRequest for feedback only; the backend decides.
import { ApiError } from '../../apis/apiError';

/** FacilityOrganizationForm values and labels (Điều 39 NĐ 96/2023/NĐ-CP). */
export const ORGANIZATION_FORMS = [
  { value: 'BENH_VIEN_DA_KHOA', label: 'Bệnh viện đa khoa' },
  { value: 'BENH_VIEN_CHUYEN_KHOA', label: 'Bệnh viện chuyên khoa' },
  { value: 'BENH_VIEN_Y_HOC_CO_TRUYEN', label: 'Bệnh viện y học cổ truyền' },
  { value: 'BENH_VIEN_RANG_HAM_MAT', label: 'Bệnh viện răng hàm mặt' },
  { value: 'PHONG_KHAM_DA_KHOA', label: 'Phòng khám đa khoa' },
  { value: 'PHONG_KHAM_CHUYEN_KHOA', label: 'Phòng khám chuyên khoa' },
  { value: 'PHONG_KHAM_LIEN_CHUYEN_KHOA', label: 'Phòng khám liên chuyên khoa' },
  { value: 'PHONG_KHAM_BAC_SY_Y_KHOA', label: 'Phòng khám bác sỹ y khoa' },
  { value: 'PHONG_KHAM_Y_HOC_CO_TRUYEN', label: 'Phòng khám y học cổ truyền' },
  { value: 'PHONG_KHAM_RANG_HAM_MAT', label: 'Phòng khám răng hàm mặt' },
  { value: 'PHONG_KHAM_DINH_DUONG', label: 'Phòng khám dinh dưỡng' },
  { value: 'PHONG_KHAM_Y_SY_DA_KHOA', label: 'Phòng khám y sỹ đa khoa' },
  { value: 'TRAM_Y_TE', label: 'Trạm y tế' },
  { value: 'NHA_HO_SINH', label: 'Nhà hộ sinh' },
  { value: 'PHONG_CHAN_TRI_Y_HOC_CO_TRUYEN', label: 'Phòng chẩn trị y học cổ truyền' },
  { value: 'CO_SO_Y_HOC_GIA_DINH', label: 'Cơ sở khám bệnh, chữa bệnh y học gia đình' },
];

export const FIELDS = ['maCskcb', 'tenCskcb', 'diachi', 'sodienthoai', 'soGiayPhepHoatDong', 'hinhThucToChuc'];

export const FIELD_LABEL = {
  maCskcb: 'Mã cơ sở KBCB',
  tenCskcb: 'Tên cơ sở',
  diachi: 'Địa chỉ',
  sodienthoai: 'Số điện thoại',
  soGiayPhepHoatDong: 'Số giấy phép hoạt động',
  hinhThucToChuc: 'Hình thức tổ chức',
};

const MAX_LENGTH = { tenCskcb: 255, diachi: 1024, soGiayPhepHoatDong: 100 };
const CODE_PATTERN = /^[0-9]{5}$/;
const PHONE_PATTERN = /^(?:0[35789][0-9]{8}|02[0-9]{9}|1[89]00[0-9]{4}(?:[0-9]{2})?)$/;

export function facilityToForm(facility) {
  const form = {};
  FIELDS.forEach((field) => {
    form[field] = facility?.configured ? facility[field] ?? '' : '';
  });
  return form;
}

/** Removes the spaces, dots and dashes people type in phone numbers. */
export function normalizePhone(value) {
  return value.replace(/[\s.-]/g, '');
}

/** Request body; version is null for the first declaration. */
export function formToRequest(form, facility) {
  return {
    maCskcb: form.maCskcb.trim(),
    tenCskcb: form.tenCskcb.trim(),
    diachi: form.diachi.trim(),
    sodienthoai: normalizePhone(form.sodienthoai),
    soGiayPhepHoatDong: form.soGiayPhepHoatDong.trim(),
    hinhThucToChuc: form.hinhThucToChuc,
    version: facility?.configured ? facility.version : null,
  };
}

export function validateFacilityForm(form) {
  const errors = {};
  FIELDS.forEach((field) => {
    if (!String(form[field] ?? '').trim()) {
      errors[field] = field === 'hinhThucToChuc'
        ? 'Vui lòng chọn hình thức tổ chức.'
        : `Vui lòng nhập ${FIELD_LABEL[field].toLowerCase()}.`;
    }
  });
  Object.entries(MAX_LENGTH).forEach(([field, max]) => {
    if (!errors[field] && form[field].trim().length > max) {
      errors[field] = `${FIELD_LABEL[field]} không được vượt quá ${max} ký tự.`;
    }
  });
  if (!errors.maCskcb && !CODE_PATTERN.test(form.maCskcb.trim())) {
    errors.maCskcb = 'Mã cơ sở KBCB gồm đúng 5 chữ số.';
  }
  if (!errors.sodienthoai && !PHONE_PATTERN.test(normalizePhone(form.sodienthoai))) {
    errors.sodienthoai = 'Số điện thoại không đúng định dạng.';
  }
  return errors;
}

/** Fields whose saved value would change. Phone is compared after normalization. */
export function changedFields(form, initialForm) {
  return FIELDS.filter((field) => {
    const normalize = field === 'sodienthoai' ? normalizePhone : (value) => value.trim();
    return normalize(form[field] ?? '') !== normalize(initialForm[field] ?? '');
  });
}

/** BR-77-03: address or organization-form changes usually need a new operating licence. */
export function needsLicenceReminder(changed) {
  return changed.includes('diachi') || changed.includes('hinhThucToChuc');
}

export const MSG_CODE_LOCKED =
  'Không thể đổi mã cơ sở KBCB vì đã có lượt khám. Các thông tin khác vẫn cập nhật được.';
export const MSG_CONCURRENT =
  'Thông tin cơ sở vừa được thay đổi bởi thao tác khác. Dữ liệu mới nhất đã được tải lại, vui lòng kiểm tra rồi lưu lại.';
const MSG_FORBIDDEN = 'Bạn không có quyền thực hiện thao tác này.';
const MSG_SESSION = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
const MSG_INVALID = 'Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại.';
const MSG_NETWORK = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
const MSG_FALLBACK = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';

/**
 * Classifies a failed load or save. reload is true when the page must fetch the facility again
 * (EF-02 code locked, EF-03 stale version).
 */
export function facilityError(error) {
  if (!(error instanceof ApiError)) return { message: MSG_FALLBACK };
  const code = error.errorResponse?.code;
  if (code === 'FACILITY_CODE_LOCKED') return { message: MSG_CODE_LOCKED, reload: true };
  if (code === 'CONCURRENT_MODIFICATION') return { message: MSG_CONCURRENT, reload: true };
  if (code === 'REQUEST_VALIDATION_FAILED') {
    const fields = {};
    Object.keys(error.errorResponse?.details ?? {}).forEach((field) => {
      if (FIELD_LABEL[field]) fields[field] = `${FIELD_LABEL[field]} không hợp lệ.`;
    });
    return { message: MSG_INVALID, fields };
  }
  if (error.status === 401) return { message: MSG_SESSION };
  if (error.status === 403) return { message: MSG_FORBIDDEN };
  if (!code && error.status === undefined) return { message: MSG_NETWORK };
  return { message: MSG_FALLBACK };
}
