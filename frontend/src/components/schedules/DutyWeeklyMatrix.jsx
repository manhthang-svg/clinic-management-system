import { useState, useMemo, Fragment } from 'react';
import { getVietnamTodayStr, isShiftPast } from '../../utils/vietnamTimeUtils';

/**
 * Trợ giúp lấy vai trò và chức danh hiển thị: Bác sĩ, KTV, Điều dưỡng
 */
function getStaffRole(item, staffList) {
  let role = item.staffRole;
  if (!role && item.staffAccountId && staffList?.length) {
    const s = staffList.find((it) => it.id === item.staffAccountId);
    if (s?.role) role = s.role;
  }
  const isDoctor =
    role === 'DOCTOR' ||
    (item.staffName && (item.staffName.includes('BS') || item.staffName.includes('Bác sĩ')));
  const isTech =
    role === 'TECHNICIAN' ||
    (item.staffName && item.staffName.includes('KTV'));

  if (isDoctor) return 'Bác sĩ';
  if (isTech) return 'KTV';
  if (role === 'NURSE') return 'Điều dưỡng';
  return role || '';
}

export function DutyWeeklyMatrix({
  days = [],
  roomList = [],
  staffList = [],
  scheduleItems = [],
  shiftFilter = 'ALL', // 'ALL' | 'MORNING' | 'AFTERNOON'
  canEdit = false,
  onAddDutySlot, // (roomId, dateStr, shift) => void
  onEditDuty, // (item) => void
  onDeleteDuty, // (item) => void
}) {
  const todayStr = getVietnamTodayStr();

  // Trạng thái thu gọn nhóm khoa (mặc định mở rộng tất cả)
  const [collapsedDepts, setCollapsedDepts] = useState(new Set());

  // Toggle thu gọn/mở rộng 1 khoa
  const toggleDept = (deptName) => {
    setCollapsedDepts((prev) => {
      const next = new Set(prev);
      if (next.has(deptName)) {
        next.delete(deptName);
      } else {
        next.add(deptName);
      }
      return next;
    });
  };

  // Tra cứu ca trực theo Map: `${roomId}_${workDate}_${shift}` => item
  const scheduleMap = useMemo(() => {
    const map = new Map();
    scheduleItems.forEach((item) => {
      let rId = item.roomId;
      if (!rId && item.staffAccountId) {
        const staff = staffList.find((s) => s.id === item.staffAccountId);
        if (staff?.roomId) rId = staff.roomId;
      }
      if (rId && item.workDate && item.shift) {
        map.set(`${rId}_${item.workDate}_${item.shift}`, item);
      }
    });
    return map;
  }, [scheduleItems, staffList]);

  // Gom nhóm phòng theo Khoa
  const groupedRooms = useMemo(() => {
    const groups = {};
    roomList.forEach((r) => {
      let dept = r.departmentName;
      if (!dept) {
        dept = r.type === 'PARACLINICAL' ? 'Cận lâm sàng' : 'Khoa Khám Bệnh';
      }
      if (!groups[dept]) groups[dept] = [];
      groups[dept].push(r);
    });
    return Object.entries(groups).map(([deptName, rooms]) => ({
      deptName,
      rooms,
    }));
  }, [roomList]);

  const hasToday = useMemo(() => days.some((d) => d.dateStr === todayStr), [days, todayStr]);

  return (
    <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm overflow-hidden flex flex-col">
      <div className="overflow-x-auto max-h-[calc(100vh-250px)] relative">
        <table className="w-full table-fixed border-collapse text-left min-w-[950px]">
          {/* Cố định tỷ lệ 8 cột chuẩn 100% bằng colgroup: 1 cột phòng + 7 cột ngày */}
          <colgroup>
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
            <col style={{ width: '12.5%' }} />
          </colgroup>

          {/* Header Bảng: Trục ngang là 7 ngày trong tuần */}
          <thead>
            <tr className="bg-[#f8fafc] border-b border-slate-200 text-slate-800">
              {/* Cột 1 cố định (Sticky top & left): Phòng khám */}
              <th className="sticky top-0 left-0 z-30 bg-[#f8fafc] border-r border-slate-200 py-2.5 px-2 text-center font-bold text-xs shadow-2xs">
                Phòng khám
              </th>

              {/* 7 cột ngày trong tuần (Sticky top) - Căn chỉnh thẳng hàng 100% */}
              {days.map((day, idx) => {
                const isHighlight = hasToday ? day.dateStr === todayStr : idx === 0;
                return (
                  <th
                    key={day.dateStr}
                    className="sticky top-0 z-20 bg-[#f8fafc] border-r border-slate-200 last:border-r-0 py-2 px-1 text-center"
                  >
                    <div className="flex flex-col items-center justify-center">
                      <span className="text-xs font-bold text-slate-800 leading-tight">
                        {day.label}
                      </span>
                      <div className="mt-1">
                        {isHighlight ? (
                          <span className="bg-teal-600 text-white rounded-full px-2.5 py-0.5 text-[11px] font-bold inline-block shadow-2xs">
                            {day.displayDate}
                          </span>
                        ) : (
                          <span className="text-slate-700 text-[11px] font-semibold inline-block py-0.5">
                            {day.displayDate}
                          </span>
                        )}
                      </div>
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>

          {/* Body Bảng: Sử dụng Fragment bên trong 1 tbody duy nhất để giữ thẳng cột 100% */}
          <tbody>
            {groupedRooms.length === 0 ? (
              <tr>
                <td colSpan={8} className="text-center py-12 text-slate-400 text-xs italic">
                  Không tìm thấy phòng khám nào phù hợp.
                </td>
              </tr>
            ) : (
              groupedRooms.map(({ deptName, rooms }) => {
                const isCollapsed = collapsedDepts.has(deptName);
                const displayDeptTitle = deptName.toUpperCase().startsWith('KHOA')
                  ? deptName.toUpperCase()
                  : `KHOA ${deptName.toUpperCase()}`;

                return (
                  <Fragment key={deptName}>
                    {/* Header Nhóm Khoa: "+ KHOA NỘI", "+ KHOA NGOẠI" */}
                    <tr
                      className="bg-[#edf2f7] hover:bg-[#e2e8f0] transition-colors cursor-pointer select-none border-b border-slate-200"
                      onClick={() => toggleDept(deptName)}
                    >
                      <td colSpan={8} className="px-4 py-2">
                        <div className="flex items-center gap-1 text-slate-700 font-bold text-xs tracking-wide">
                          <span>+</span>
                          <span>{displayDeptTitle}</span>
                        </div>
                      </td>
                    </tr>

                    {/* Danh sách các phòng khám trong khoa */}
                    {!isCollapsed &&
                      rooms.map((room) => {
                        const roomCode = room.code || room.roomCode || room.name;
                        const isRoomInactive =
                          room.status === 'INACTIVE' ||
                          room.status === 'inactive' ||
                          room.isActive === false ||
                          room.status === 'MAINTENANCE';

                        return (
                          <tr
                            key={room.id}
                            className={`${
                              isRoomInactive ? 'bg-slate-50/40 text-slate-400' : 'hover:bg-slate-50/50'
                            } transition-colors border-b border-slate-200`}
                          >
                            {/* Cột Tên/Mã Phòng cố định bên trái (Sticky left) - Căn trái chuẩn Image 2 */}
                            <td
                              className={`sticky left-0 z-10 ${
                                isRoomInactive ? 'bg-slate-50 text-slate-500' : 'bg-white text-slate-700'
                              } px-4 py-3 text-left font-semibold text-xs sm:text-sm border-r border-slate-200 align-middle shadow-2xs`}
                              title={`${room.name || room.roomName || roomCode}${isRoomInactive ? ' (Tạm ngừng)' : ''}`}
                            >
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <span className={`truncate ${isRoomInactive ? 'text-slate-500' : 'text-slate-700'}`}>
                                  {roomCode}
                                </span>
                                {isRoomInactive && (
                                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-rose-50 text-rose-600 border border-rose-200/80 shrink-0 select-none">
                                    Tạm ngừng
                                  </span>
                                )}
                              </div>
                            </td>

                            {/* 7 ô giao điểm theo từng ngày trong tuần */}
                            {days.map((day, dayIdx) => {
                              const isMorningPast = isShiftPast(day.dateStr, 'MORNING');
                              const canEditMorning = canEdit && !isMorningPast && !isRoomInactive;

                              const isAfternoonPast = isShiftPast(day.dateStr, 'AFTERNOON');
                              const canEditAfternoon = canEdit && !isAfternoonPast && !isRoomInactive;

                              const morningItem = scheduleMap.get(
                                `${room.id}_${day.dateStr}_MORNING`
                              );
                              const afternoonItem = scheduleMap.get(
                                `${room.id}_${day.dateStr}_AFTERNOON`
                              );

                              const morningRole = morningItem
                                ? getStaffRole(morningItem, staffList)
                                : '';
                              const afternoonRole = afternoonItem
                                ? getStaffRole(afternoonItem, staffList)
                                : '';

                              return (
                                <td
                                  key={`${room.id}_${day.dateStr}`}
                                  className={`p-1.5 align-middle border-r border-slate-200 last:border-r-0 ${
                                    isRoomInactive ? 'bg-slate-50/50' : 'bg-white'
                                  }`}
                                >
                                  {/* Trường hợp 1: Chế độ xem Tất cả ca (2 slot Sáng & Chiều) */}
                                  {shiftFilter === 'ALL' && (
                                    <div className="space-y-1.5 flex flex-col justify-center">
                                      {/* Slot Sáng (Dòng 1: Toàn bộ tông vàng) */}
                                      {morningItem ? (
                                        <div
                                          onClick={() => onEditDuty(morningItem)}
                                          className={`bg-[#fffbeb] border border-amber-300 rounded-md p-1.5 text-xs flex items-center justify-between gap-1 select-none transition-all truncate cursor-pointer hover:bg-amber-100 hover:border-amber-400 hover:shadow-2xs ${
                                            isRoomInactive ? 'opacity-70' : ''
                                          }`}
                                          title={`Ca Sáng: ${morningItem.staffName} ${
                                            morningRole ? `(${morningRole})` : ''
                                          }${isMorningPast ? ' (Đã kết thúc)' : ''}${
                                            isRoomInactive ? ' - Phòng tạm ngừng' : ''
                                          }`}
                                        >
                                          <span className="font-bold text-amber-950 truncate text-[11px]">
                                            {morningItem.staffName}
                                          </span>
                                          {morningRole && (
                                            <span className="text-amber-700 font-medium text-[10.5px] shrink-0">
                                              ({morningRole})
                                            </span>
                                          )}
                                        </div>
                                      ) : isMorningPast ? (
                                        /* Ca sáng trong quá khứ và chưa phân công: ẩn, không click được */
                                        <div className="h-[27px] flex items-center justify-center select-none text-slate-300 text-xs">
                                          -
                                        </div>
                                      ) : isRoomInactive ? (
                                        /* Phòng đang tạm khóa: hiện mờ mờ không click được */
                                        <div
                                          className="bg-slate-100/70 border border-dashed border-slate-200/90 rounded-md p-1.5 text-xs flex items-center justify-center gap-1 select-none cursor-not-allowed opacity-50"
                                          title="Phòng đang tạm ngừng hoạt động - Không thể phân công"
                                        >
                                          <span className="text-slate-400 font-normal text-[11px] truncate">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : canEditMorning ? (
                                        <div
                                          onClick={() =>
                                            onAddDutySlot(room.id, day.dateStr, 'MORNING')
                                          }
                                          className="bg-[#fffdf5] border border-dashed border-amber-300 rounded-md p-1.5 text-xs flex items-center justify-center gap-1 transition-colors select-none cursor-pointer hover:bg-amber-100/70 hover:border-amber-400"
                                          title="Nhấn để phân công ca sáng"
                                        >
                                          <span className="text-amber-700/80 font-medium text-[11px] truncate">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : (
                                        <div
                                          className="bg-[#fffdf5]/60 border border-amber-200/50 rounded-md p-1.5 text-xs flex items-center justify-center gap-1 select-none cursor-default"
                                          title="Ca sáng chưa phân công"
                                        >
                                          <span className="text-amber-600/50 font-normal text-[11px] truncate">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      )}

                                      {/* Slot Chiều (Dòng 2: Toàn bộ tông xanh) */}
                                      {afternoonItem ? (
                                        <div
                                          onClick={() => onEditDuty(afternoonItem)}
                                          className={`bg-[#f0f9ff] border border-sky-300 rounded-md p-1.5 text-xs flex items-center justify-between gap-1 select-none transition-all truncate cursor-pointer hover:bg-sky-100 hover:border-sky-400 hover:shadow-2xs ${
                                            isRoomInactive ? 'opacity-70' : ''
                                          }`}
                                          title={`Ca Chiều: ${afternoonItem.staffName} ${
                                            afternoonRole ? `(${afternoonRole})` : ''
                                          }${isAfternoonPast ? ' (Đã kết thúc)' : ''}${
                                            isRoomInactive ? ' - Phòng tạm ngừng' : ''
                                          }`}
                                        >
                                          <span className="font-bold text-sky-950 truncate text-[11px]">
                                            {afternoonItem.staffName}
                                          </span>
                                          {afternoonRole && (
                                            <span className="text-sky-700 font-medium text-[10.5px] shrink-0">
                                              ({afternoonRole})
                                            </span>
                                          )}
                                        </div>
                                      ) : isAfternoonPast ? (
                                        /* Ca chiều trong quá khứ và chưa phân công: ẩn, không click được */
                                        <div className="h-[27px] flex items-center justify-center select-none text-slate-300 text-xs">
                                          -
                                        </div>
                                      ) : isRoomInactive ? (
                                        /* Phòng đang tạm khóa: hiện mờ mờ không click được */
                                        <div
                                          className="bg-slate-100/70 border border-dashed border-slate-200/90 rounded-md p-1.5 text-xs flex items-center justify-center gap-1 select-none cursor-not-allowed opacity-50"
                                          title="Phòng đang tạm ngừng hoạt động - Không thể phân công"
                                        >
                                          <span className="text-slate-400 font-normal text-[11px] truncate">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : canEditAfternoon ? (
                                        <div
                                          onClick={() =>
                                            onAddDutySlot(room.id, day.dateStr, 'AFTERNOON')
                                          }
                                          className="bg-[#f8fcff] border border-dashed border-sky-300 rounded-md p-1.5 text-xs flex items-center justify-center gap-1 transition-colors select-none cursor-pointer hover:bg-sky-100/70 hover:border-sky-400"
                                          title="Nhấn để phân công ca chiều"
                                        >
                                          <span className="text-sky-700/80 font-medium text-[11px] truncate">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : (
                                        <div
                                          className="bg-[#f8fcff]/60 border border-sky-200/50 rounded-md p-1.5 text-xs flex items-center justify-center gap-1 select-none cursor-default"
                                          title="Ca chiều chưa phân công"
                                        >
                                          <span className="text-sky-600/50 font-normal text-[11px] truncate">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      )}
                                    </div>
                                  )}

                                  {/* Trường hợp 2: Chỉ xem Ca Sáng (rộng thoáng, toàn bộ màu vàng) */}
                                  {shiftFilter === 'MORNING' && (
                                    <div>
                                      {morningItem ? (
                                        <div
                                          onClick={() => onEditDuty(morningItem)}
                                          className={`bg-[#fffbeb] border border-amber-300 rounded-lg p-2.5 text-xs flex items-center justify-between gap-2 select-none transition-all truncate cursor-pointer hover:bg-amber-100 hover:border-amber-400 hover:shadow-2xs ${
                                            isRoomInactive ? 'opacity-70' : ''
                                          }`}
                                          title={`Ca Sáng: ${morningItem.staffName} ${
                                            morningRole ? `(${morningRole})` : ''
                                          }${isMorningPast ? ' (Đã kết thúc)' : ''}${
                                            isRoomInactive ? ' - Phòng tạm ngừng' : ''
                                          }`}
                                        >
                                          <span className="font-bold text-amber-950 text-xs truncate">
                                            {morningItem.staffName}
                                          </span>
                                          {morningRole && (
                                            <span className="text-amber-700 font-medium text-[11px] shrink-0">
                                              ({morningRole})
                                            </span>
                                          )}
                                        </div>
                                      ) : isMorningPast ? (
                                        /* Ca sáng trong quá khứ và chưa phân công: ẩn, không click được */
                                        <div className="h-[38px] flex items-center justify-center select-none text-slate-300 text-xs">
                                          -
                                        </div>
                                      ) : isRoomInactive ? (
                                        /* Phòng đang tạm khóa: hiện mờ mờ không click được */
                                        <div
                                          className="bg-slate-100/70 border border-dashed border-slate-200/90 rounded-lg p-2.5 text-xs flex items-center justify-center gap-2 select-none cursor-not-allowed opacity-50"
                                          title="Phòng đang tạm ngừng hoạt động - Không thể phân công"
                                        >
                                          <span className="text-slate-400 font-normal text-xs">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : canEditMorning ? (
                                        <div
                                          onClick={() =>
                                            onAddDutySlot(room.id, day.dateStr, 'MORNING')
                                          }
                                          className="bg-[#fffdf5] border border-dashed border-amber-300 rounded-lg p-2.5 text-xs flex items-center justify-center gap-2 transition-colors select-none cursor-pointer hover:bg-amber-100/70 hover:border-amber-400"
                                          title="Nhấn để phân công ca sáng"
                                        >
                                          <span className="text-amber-700/80 font-medium text-xs">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : (
                                        <div
                                          className="bg-[#fffdf5]/60 border border-amber-200/50 rounded-lg p-2.5 text-xs flex items-center justify-center gap-2 select-none cursor-default"
                                          title="Ca sáng chưa phân công"
                                        >
                                          <span className="text-amber-600/50 font-normal text-xs">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      )}
                                    </div>
                                  )}

                                  {/* Trường hợp 3: Chỉ xem Ca Chiều (rộng thoáng, toàn bộ màu xanh) */}
                                  {shiftFilter === 'AFTERNOON' && (
                                    <div>
                                      {afternoonItem ? (
                                        <div
                                          onClick={() => onEditDuty(afternoonItem)}
                                          className={`bg-[#f0f9ff] border border-sky-300 rounded-lg p-2.5 text-xs flex items-center justify-between gap-2 select-none transition-all truncate cursor-pointer hover:bg-sky-100 hover:border-sky-400 hover:shadow-2xs ${
                                            isRoomInactive ? 'opacity-70' : ''
                                          }`}
                                          title={`Ca Chiều: ${afternoonItem.staffName} ${
                                            afternoonRole ? `(${afternoonRole})` : ''
                                          }${isAfternoonPast ? ' (Đã kết thúc)' : ''}${
                                            isRoomInactive ? ' - Phòng tạm ngừng' : ''
                                          }`}
                                        >
                                          <span className="font-bold text-sky-950 text-xs truncate">
                                            {afternoonItem.staffName}
                                          </span>
                                          {afternoonRole && (
                                            <span className="text-sky-700 font-medium text-[11px] shrink-0">
                                              ({afternoonRole})
                                            </span>
                                          )}
                                        </div>
                                      ) : isAfternoonPast ? (
                                        /* Ca chiều trong quá khứ và chưa phân công: ẩn, không click được */
                                        <div className="h-[38px] flex items-center justify-center select-none text-slate-300 text-xs">
                                          -
                                        </div>
                                      ) : isRoomInactive ? (
                                        /* Phòng đang tạm khóa: hiện mờ mờ không click được */
                                        <div
                                          className="bg-slate-100/70 border border-dashed border-slate-200/90 rounded-lg p-2.5 text-xs flex items-center justify-center gap-2 select-none cursor-not-allowed opacity-50"
                                          title="Phòng đang tạm ngừng hoạt động - Không thể phân công"
                                        >
                                          <span className="text-slate-400 font-normal text-xs">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : canEditAfternoon ? (
                                        <div
                                          onClick={() =>
                                            onAddDutySlot(room.id, day.dateStr, 'AFTERNOON')
                                          }
                                          className="bg-[#f8fcff] border border-dashed border-sky-300 rounded-lg p-2.5 text-xs flex items-center justify-center gap-2 transition-colors select-none cursor-pointer hover:bg-sky-100/70 hover:border-sky-400"
                                          title="Nhấn để phân công ca chiều"
                                        >
                                          <span className="text-sky-700/80 font-medium text-xs">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      ) : (
                                        <div
                                          className="bg-[#f8fcff]/60 border border-sky-200/50 rounded-lg p-2.5 text-xs flex items-center justify-center gap-2 select-none cursor-default"
                                          title="Ca chiều chưa phân công"
                                        >
                                          <span className="text-sky-600/50 font-normal text-xs">
                                            [Chưa phân công]
                                          </span>
                                        </div>
                                      )}
                                    </div>
                                  )}
                                </td>
                              );
                            })}
                          </tr>
                        );
                      })}
                  </Fragment>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
