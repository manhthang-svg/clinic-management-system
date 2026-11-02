/**
 * Tiện ích xử lý ngày và giờ theo múi giờ Việt Nam (Asia/Ho_Chi_Minh - UTC+7)
 */

export const VN_TIMEZONE = 'Asia/Ho_Chi_Minh';

// Giờ kết thúc các ca trực tại phòng khám
export const SHIFT_END_TIMES = {
  MORNING: { hour: 11, minute: 30 }, // Ca sáng kết thúc lúc 11:30
  AFTERNOON: { hour: 17, minute: 30 }, // Ca chiều kết thúc lúc 17:30
};

/**
 * Lấy ngày hôm nay theo chuẩn múi giờ Việt Nam (YYYY-MM-DD)
 */
export function getVietnamTodayStr() {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: VN_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(new Date());
}

/**
 * Lấy giờ & phút hiện tại theo múi giờ Việt Nam
 */
export function getVietnamCurrentTime() {
  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone: VN_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  const parts = formatter.format(new Date()).split(':');
  return {
    hour: parseInt(parts[0], 10),
    minute: parseInt(parts[1], 10),
    second: parseInt(parts[2], 10),
    todayStr: getVietnamTodayStr(),
  };
}

/**
 * Kiểm tra xem một ca trực (workDate, shift) đã kết thúc hoặc thuộc thời gian trong quá khứ hay chưa
 * @param {string} workDate - Ngày trực dạng 'YYYY-MM-DD'
 * @param {'MORNING' | 'AFTERNOON'} [shift] - Ca làm việc
 * @returns {boolean} - true nếu ca trực đã qua giờ thực tế tại Việt Nam
 */
export function isShiftPast(workDate, shift) {
  if (!workDate) return false;
  const todayVN = getVietnamTodayStr();

  // 1. Ngày trực trước ngày hôm nay theo giờ VN -> Đã qua
  if (workDate < todayVN) {
    return true;
  }

  // 2. Ngày trực sau ngày hôm nay -> Trong tương lai
  if (workDate > todayVN) {
    return false;
  }

  // 3. Ngày trực là hôm nay: Kiểm tra giờ kết thúc của ca làm việc
  if (workDate === todayVN && shift) {
    const { hour, minute } = getVietnamCurrentTime();
    const currentMinutes = hour * 60 + minute;

    const endTime = SHIFT_END_TIMES[shift];
    if (endTime) {
      const endMinutes = endTime.hour * 60 + endTime.minute;
      return currentMinutes >= endMinutes;
    }
  }

  return false;
}
