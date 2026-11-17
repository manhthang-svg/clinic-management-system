import { describe, expect, it } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { medicineLoadErrorMessage, medicineSaveErrors, medicineStatusErrorMessage } from './medicineErrors';

const apiError = (status, code, details) =>
  new ApiError('backend message', { status, errorResponse: { code, message: 'raw', details } });

describe('medicine errors', () => {
  it('shows MSG07 on the duplicated field', () => {
    expect(medicineSaveErrors(apiError(409, 'MEDICINE_CODE_ALREADY_EXISTS')).fields)
      .toEqual({ code: 'Mã thuốc đã tồn tại trong hệ thống.' });
    expect(medicineSaveErrors(apiError(409, 'MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS')).fields)
      .toEqual({ registrationNumber: 'Số đăng ký đã tồn tại trong hệ thống.' });
  });

  it('maps a route outside the list to MSG40 and other rejected fields to MSG05', () => {
    expect(medicineSaveErrors(apiError(400, 'REQUEST_VALIDATION_FAILED', { route: 'NOT_IN_ROUTE_LIST', name: 'x' })).fields)
      .toEqual({
        route: 'Đường dùng đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.',
        name: 'Tên thuốc không đúng định dạng.',
      });
  });

  it('never shows the raw backend message', () => {
    expect(medicineSaveErrors(apiError(409, 'CONCURRENT_MODIFICATION')).message)
      .toBe('Thuốc vừa được cập nhật. Vui lòng tải lại và thử lại.');
    expect(medicineSaveErrors(new Error('network')).message)
      .toBe('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
    expect(medicineLoadErrorMessage(apiError(403, 'FORBIDDEN'))).toBe('Bạn không có quyền thực hiện thao tác này.');
    expect(medicineStatusErrorMessage(apiError(404, 'MEDICINE_NOT_FOUND'))).toBe('Không tìm thấy thuốc.');
  });
});
