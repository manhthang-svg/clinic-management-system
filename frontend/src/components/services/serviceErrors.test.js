import { describe, expect, it } from 'vitest';
import { ApiError } from '../../apis/apiError';
import {
  serviceCreateErrors,
  serviceErrorMessage,
  serviceStatusErrorMessage,
  serviceUpdateErrors,
} from './serviceErrors';

const apiError = (status, code, details) =>
  new ApiError(code, { status, errorResponse: { code, message: code, details } });

describe('serviceCreateErrors (UC-22)', () => {
  it('shows a duplicate service code or name under its field (EF-03, MSG07)', () => {
    expect(serviceCreateErrors(apiError(409, 'SERVICE_CODE_ALREADY_EXISTS')).fields)
      .toEqual({ code: 'Mã dịch vụ đã tồn tại trong hệ thống.' });
    expect(serviceCreateErrors(apiError(409, 'SERVICE_NAME_ALREADY_EXISTS')).fields)
      .toEqual({ name: 'Tên dịch vụ đã tồn tại trong hệ thống.' });
  });

  it('names the service whose group has no active room (EF-05, MSG73)', () => {
    const result = serviceCreateErrors(apiError(409, 'SERVICE_GROUP_HAS_NO_ACTIVE_ROOM'), 'Siêu âm tuyến giáp');

    expect(result.message).toBe(
      'Nhóm dịch vụ của Siêu âm tuyến giáp chưa có phòng nào đang hoạt động. Vui lòng kích hoạt hoặc tạo phòng trước.',
    );
  });

  it('reports a rejected field as a wrong format (MSG05) and anything else as MSG80', () => {
    expect(serviceCreateErrors(apiError(400, 'REQUEST_VALIDATION_FAILED', { clinicPrice: 'x' })).fields)
      .toEqual({ clinicPrice: 'Giá viện phí không đúng định dạng.' });
    expect(serviceCreateErrors(apiError(500, 'INTERNAL_SERVER_ERROR')).message)
      .toBe('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
  });
});

describe('shared messages', () => {
  it('uses MSG12 for an expired session and MSG13 for a missing permission', () => {
    expect(serviceErrorMessage(apiError(401, 'UNAUTHORIZED')))
      .toBe('Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.');
    expect(serviceErrorMessage(apiError(403, 'FORBIDDEN'))).toBe('Bạn không có quyền thực hiện thao tác này.');
  });

  it('uses MSG75 when the service was changed by someone else (BR-119)', () => {
    expect(serviceUpdateErrors(apiError(409, 'CONCURRENT_MODIFICATION')).message)
      .toBe('Dịch vụ vừa được người khác cập nhật. Vui lòng tải lại và thử lại.');
  });
});

describe('serviceStatusErrorMessage (UC-25)', () => {
  it('refuses to deactivate the examination service (MSG52)', () => {
    expect(serviceStatusErrorMessage(apiError(409, 'EXAMINATION_SERVICE_NOT_DEACTIVATABLE')))
      .toBe('Đây là dịch vụ khám duy nhất nên không thể ngừng hoạt động.');
  });

  it('names the service whose unfinished orders block the deactivation (MSG55)', () => {
    const error = apiError(409, 'SERVICE_HAS_UNFINISHED_ORDERS', { unfinishedOrderCount: '3' });

    expect(serviceStatusErrorMessage(error, 'Dịch vụ "Siêu âm ổ bụng"')).toBe(
      'Dịch vụ "Siêu âm ổ bụng" vẫn còn bệnh nhân hoặc dịch vụ đang chờ hoặc đang thực hiện nên hiện không thể thay đổi.',
    );
  });

  it('names the service whose group has no active room on activation (MSG73)', () => {
    expect(serviceStatusErrorMessage(apiError(409, 'SERVICE_GROUP_HAS_NO_ACTIVE_ROOM'), 'Dịch vụ "X"', 'Chụp CT'))
      .toBe('Nhóm dịch vụ của Chụp CT chưa có phòng nào đang hoạt động. Vui lòng kích hoạt hoặc tạo phòng trước.');
  });
});
