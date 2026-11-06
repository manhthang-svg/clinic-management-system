/**
 * Tiện ích xử lý ngày và giờ theo múi giờ Việt Nam (Asia/Ho_Chi_Minh - UTC+7)
 */

export const VN_TIMEZONE = 'Asia/Ho_Chi_Minh';

// Giờ kết thúc mặc định các ca trực tại phòng khám
export const SHIFT_END_TIMES = {
  MORNING: { hour: 11, minute: 30 }, // Ca sáng mặc định kết thúc lúc 11:30
  AFTERNOON: { hour: 17, minute: 30 }, // Ca chiều mặc định kết thúc lúc 17:30
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
 * @param {object} [shiftConfig] - Cấu hình khung giờ ca trực ({ morningEndTime, afternoonEndTime })
 * @returns {boolean} - true nếu ca trực đã qua giờ thực tế tại Việt Nam (bị khóa)
 */
export function isShiftPast(workDate, shift, shiftConfig = null) {
  if (!workDate) return false;
  const todayVN = getVietnamTodayStr();

  // 1. Ngày trực trước ngày hôm nay theo giờ VN -> Đã qua (tự động khóa)
  if (workDate < todayVN) {
    return true;
  }

  // 2. Ngày trực sau ngày hôm nay -> Trong tương lai (cho phép sửa bình thường)
  if (workDate > todayVN) {
    return false;
  }

  // 3. Ngày trực là hôm nay: Kiểm tra giờ kết thúc của ca làm việc
  if (workDate === todayVN && shift) {
    const { hour, minute } = getVietnamCurrentTime();
    const currentMinutes = hour * 60 + minute;

    if (shift === 'MORNING') {
      let endHour = SHIFT_END_TIMES.MORNING.hour;
      let endMinute = SHIFT_END_TIMES.MORNING.minute;

      if (shiftConfig?.morningEndTime && typeof shiftConfig.morningEndTime === 'string') {
        const [h, m] = shiftConfig.morningEndTime.split(':').map(Number);
        if (!isNaN(h) && !isNaN(m)) {
          endHour = h;
          endMinute = m;
        }
      }

      const endMinutes = endHour * 60 + endMinute;
      // Ca sáng tự động khóa khi đã quá giờ kết thúc ca hoặc quá 11:59
      return currentMinutes >= endMinutes || currentMinutes > 11 * 60 + 59;
    }

    if (shift === 'AFTERNOON') {
      let endHour = SHIFT_END_TIMES.AFTERNOON.hour;
      let endMinute = SHIFT_END_TIMES.AFTERNOON.minute;

      if (shiftConfig?.afternoonEndTime && typeof shiftConfig.afternoonEndTime === 'string') {
        const [h, m] = shiftConfig.afternoonEndTime.split(':').map(Number);
        if (!isNaN(h) && !isNaN(m)) {
          endHour = h;
          endMinute = m;
        }
      }

      const endMinutes = endHour * 60 + endMinute;
      // Ca chiều tự động khóa khi đã quá giờ kết thúc ca hoặc quá 23:59
      return currentMinutes >= endMinutes;
    }
  }

  return false;
}
