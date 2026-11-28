// UC-66: thong bao va ham dung chung cho hang doi KTV va hop thoai tra ve hang cho

export const RETURN_REASON_MAX_LENGTH = 500;

// ly do tra ve hang cho hay gap (KTV co the tu nhap)
export const RETURN_REASONS = [
  'Bệnh nhân chưa chuẩn bị (chưa nhịn ăn, chưa uống nước, chưa nhịn tiểu…)',
  'Bệnh nhân không muốn thực hiện nữa',
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

// doi loi thao tac UC-66 thanh thong bao tieng Viet
export function performErrorMessage(error) {
  const code = error?.errorResponse?.code;
  if (error?.status === 401) return PERFORM_MESSAGES.sessionExpired;
  if (code === 'PARACLINICAL_SERVICE_NOT_IN_PROGRESS') return PERFORM_MESSAGES.notInProgress;
  if (error?.status === 403) return PERFORM_MESSAGES.notPerformer;
  return PERFORM_MESSAGES.systemError;
}

// kiem tra ly do tra ve, hop le thi tra ''
export function validateReturnReason(reason) {
  const text = String(reason ?? '').trim();
  if (!text) return PERFORM_MESSAGES.reasonRequired;
  if (text.length > RETURN_REASON_MAX_LENGTH) return PERFORM_MESSAGES.reasonTooLong;
  return '';
}
