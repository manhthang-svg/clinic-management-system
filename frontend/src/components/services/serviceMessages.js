// Report 3 system messages used by the service screens (UC-20 to UC-25), word for word.

export const MSG01 = 'Không tìm thấy kết quả.';
export const msg02 = (field) => `${field} là bắt buộc.`;
export const msg03 = (item) => `${item} cập nhật thành công.`;
export const msg04 = (item) => `Tạo thành công ${item}.`;
export const msg05 = (field) => `${field} không đúng định dạng.`;
export const msg06 = (action) => `Bạn có chắc chắn muốn ${action} không?`;
export const msg07 = (field) => `${field} đã tồn tại trong hệ thống.`;
export const msg08 = (maxLength) => `Vượt quá độ dài tối đa ${maxLength} ký tự.`;
export const MSG12 = 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.';
export const MSG13 = 'Bạn không có quyền thực hiện thao tác này.';
export const msg32 = (min, max) => `Giá phải là số nguyên từ ${min} đến ${max} VND.`;
export const MSG38 = 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?';
export const msg39 = (recordType) => `Không tìm thấy ${recordType}.`;
export const MSG50 = 'Ngày bắt đầu không được sau ngày kết thúc.';
export const msg40 = (item) => `${item} đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.`;
export const MSG52 = 'Đây là dịch vụ khám duy nhất nên không thể ngừng hoạt động.';
export const msg55 = (item) =>
  `${item} vẫn còn bệnh nhân hoặc dịch vụ đang chờ hoặc đang thực hiện nên hiện không thể thay đổi.`;
export const msg73 = (serviceName) =>
  `Nhóm dịch vụ của ${serviceName} chưa có phòng nào đang hoạt động. Vui lòng kích hoạt hoặc tạo phòng trước.`;
export const msg75 = (item) => `${item} vừa được cập nhật. Vui lòng tải lại và thử lại.`;
export const MSG80 = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
export const MSG92 = 'Chưa có dữ liệu để hiển thị.';
