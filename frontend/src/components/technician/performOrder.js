// UC-66 Perform Paraclinical Order: messages and helpers shared by the queue page and its dialog.

export const RETURN_REASON_MAX_LENGTH = 500;

/** Usual reasons of UC-66 AF-02; the technician can also type their own. */
export const RETURN_REASONS = [
  'Bệnh nhân chưa chuẩn bị (chưa nhịn ăn, chưa uống nước, chưa nhịn tiểu…)',
  'Máy/thiết bị gặp sự cố',
  'Bệnh nhân cần chờ bác sĩ chuyên khoa',
];

export const PERFORM_MESSAGES = {
  performed: 'Chỉ định cập nhật thành công.', // MSG03
  returned: 'Đã trả chỉ định về hàng chờ.',
  notPerformer: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
  notInProgress: 'Chỉ định không còn ở trạng thái đang thực hiện. Vui lòng tải lại danh sách.',
  sessionExpired: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
  systemError: 'Đã có lỗi hệ thống. Vui lòng thử lại sau.', // MSG99
  reasonRequired: 'Lý do trả về hàng chờ là bắt buộc.', // MSG02
  reasonTooLong: `Vượt quá độ dài tối đa ${RETURN_REASON_MAX_LENGTH} ký tự.`, // MSG08
};

/** Maps a failed UC-66 command to a Vietnamese message (EF-01, EF-02); never shows the raw backend text. */
export function performErrorMessage(error) {
  const code = error?.errorResponse?.code;
  if (error?.status === 401) return PERFORM_MESSAGES.sessionExpired;
  if (code === 'PARACLINICAL_SERVICE_NOT_IN_PROGRESS') return PERFORM_MESSAGES.notInProgress;
  if (error?.status === 403) return PERFORM_MESSAGES.notPerformer;
  return PERFORM_MESSAGES.systemError;
}

/** Returns the error of a return reason, or an empty string when it is valid. */
export function validateReturnReason(reason) {
  const text = String(reason ?? '').trim();
  if (!text) return PERFORM_MESSAGES.reasonRequired;
  if (text.length > RETURN_REASON_MAX_LENGTH) return PERFORM_MESSAGES.reasonTooLong;
  return '';
}
