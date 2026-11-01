import { describe, expect, it } from 'vitest';
import { ApiError } from '../../apis/apiError';
import {
  changedFields,
  facilityError,
  facilityToForm,
  formToRequest,
  needsLicenceReminder,
  validateFacilityForm,
} from './facilityForm';

const facility = {
  configured: true, maCskcb: '79001', tenCskcb: 'PK Đa khoa An Bình', diachi: '1 Lê Lợi',
  sodienthoai: '02812345678', soGiayPhepHoatDong: '01234/HCM-GPHĐ', hinhThucToChuc: 'PHONG_KHAM_DA_KHOA',
  maCskcbLocked: false, version: 3,
};

describe('facilityForm', () => {
  it('starts empty for an unconfigured facility and sends a null version', () => {
    const form = facilityToForm({ configured: false, version: null });
    expect(form.maCskcb).toBe('');
    expect(formToRequest({ ...facilityToForm(facility) }, { configured: false }).version).toBeNull();
  });

  it('validates the code, phone and required fields like the backend', () => {
    const errors = validateFacilityForm({ ...facilityToForm(facility), maCskcb: '7900', sodienthoai: '12345', tenCskcb: ' ' });
    expect(errors).toEqual({
      maCskcb: 'Mã cơ sở KBCB gồm đúng 5 chữ số.',
      sodienthoai: 'Số điện thoại không đúng định dạng.',
      tenCskcb: 'Vui lòng nhập tên cơ sở.',
    });
    expect(validateFacilityForm({ ...facilityToForm(facility), sodienthoai: '0901 234.567' })).toEqual({});
  });

  it('normalizes the phone and trims text in the request', () => {
    const request = formToRequest({ ...facilityToForm(facility), sodienthoai: '028 1234 5678', tenCskcb: ' PK ' }, facility);
    expect(request).toMatchObject({ sodienthoai: '02812345678', tenCskcb: 'PK', version: 3 });
  });

  it('ignores formatting-only edits and flags licence-related changes', () => {
    const initial = facilityToForm(facility);
    expect(changedFields({ ...initial, sodienthoai: '028.1234.5678' }, initial)).toEqual([]);
    const changed = changedFields({ ...initial, diachi: '2 Lê Lợi' }, initial);
    expect(changed).toEqual(['diachi']);
    expect(needsLicenceReminder(changed)).toBe(true);
    expect(needsLicenceReminder(['sodienthoai'])).toBe(false);
  });

  it('asks for a reload on code-locked and stale-version conflicts', () => {
    const locked = new ApiError('x', { status: 409, errorResponse: { code: 'FACILITY_CODE_LOCKED', message: 'x' } });
    const stale = new ApiError('x', { status: 409, errorResponse: { code: 'CONCURRENT_MODIFICATION', message: 'x' } });
    expect(facilityError(locked).reload).toBe(true);
    expect(facilityError(stale).reload).toBe(true);
    expect(facilityError(new ApiError('x')).message).toMatch('Không kết nối được máy chủ');
  });
});
