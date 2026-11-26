import { useId, useMemo, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { Button } from '../ui/button';

const ROOM_TYPE_LABELS = {
  EXAMINATION: 'Khám lâm sàng',
  PARACLINICAL: 'Cận lâm sàng',
  ADMINISTRATIVE: 'Hành chính / Thu ngân',
};

const CELL = 'px-3 py-2 text-xs text-slate-700';
const HEAD = 'px-3 py-2 text-[11px] font-bold text-[#1b2533] bg-[#f6f8f9] text-left';

/** A staff member with a current room and a shift today or later keeps that room (the backend rejects a change). */
function isRoomLocked(staff) {
  return Boolean(staff.roomId && staff.hasUpcomingDutySchedule);
}

const SEARCH_MAX_LENGTH = 100;

/** BR-110: matching ignores case and Vietnamese diacritics. */
function normalizeSearch(value) {
  return (value || '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

function staffMatches(staff, query) {
  if (!query) return true;
  const q = normalizeSearch(query);
  return [staff.fullName, staff.username, staff.roomCode, staff.roomName].some((field) =>
    normalizeSearch(field).includes(q)
  );
}

/**
 * UC-15 Assign Staff to Room. Shows the room, the staff already assigned to it, and the active
 * Doctors (examination room) or Technicians (paraclinical room) the Admin can assign; several at once.
 * There is no remove action: an assignment ends when the staff member is assigned elsewhere or
 * their role changes (UC-09). Staff who already have a duty shift today or later cannot change room.
 */
export function RoomAssignmentDialog({
  room,
  staffOptions,
  loading,
  selectedStaffIds,
  onToggle,
  onConfirm,
  onClose,
}) {
  const [query, setQuery] = useState('');
  const [pending, setPending] = useState(false);
  const pendingRef = useRef(false);
  const titleId = useId();
  const availableHeadingId = useId();
  const searchId = useId();

  const roleLabel = room.type === 'EXAMINATION' ? 'Bác sĩ' : 'Kỹ thuật viên';
  const assignedStaff = useMemo(
    () => staffOptions.filter((staff) => staff.roomId === room.id),
    [staffOptions, room.id]
  );
  const availableStaff = useMemo(
    () => staffOptions.filter((staff) => staff.roomId !== room.id),
    [staffOptions, room.id]
  );
  const visibleStaff = availableStaff.filter((staff) => staffMatches(staff, query.trim()));

  const confirm = async () => {
    if (pendingRef.current) return;
    pendingRef.current = true;
    setPending(true);
    try {
      await onConfirm();
    } finally {
      pendingRef.current = false;
      setPending(false);
    }
  };

  const roomInfo = [
    ['Mã phòng', room.code],
    ['Tên phòng', room.name],
    ['Khoa', room.departmentName || 'Chưa xác định khoa'],
    ['Loại phòng', ROOM_TYPE_LABELS[room.type] || '—'],
    ['Vị trí', room.location || '—'],
    ['Trạng thái', room.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngưng'],
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px]">
      <div
        className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl border border-[#e3ece9]"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <h3 id={titleId} className="text-lg font-bold text-[#142534] tracking-tight">
          Phân phòng: {room.name}
        </h3>
        <p className="mt-1 text-sm text-[#60737d]">
          {room.code} — {room.departmentName || 'Chưa xác định khoa'}
        </p>

        {/* Thông tin phòng */}
        <section className="mt-5">
          <h4 className="text-sm font-bold text-[#142534] mb-2">Thông tin phòng</h4>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 rounded-xl border border-slate-200 bg-slate-50/60 p-3">
            {roomInfo.map(([label, value]) => (
              <div key={label} className="flex gap-2 text-xs">
                <dt className="w-20 shrink-0 text-slate-500">{label}</dt>
                <dd className="font-semibold text-slate-800">{value}</dd>
              </div>
            ))}
          </dl>
        </section>

        {/* Nhân sự đang được phân công */}
        <section className="mt-5">
          <h4 className="text-sm font-bold text-[#142534] mb-2">
            Nhân sự đang được phân công ({loading ? '…' : assignedStaff.length})
          </h4>
          {!loading && assignedStaff.length === 0 ? (
            <p className="text-xs text-slate-400">Phòng chưa có nhân sự được phân công.</p>
          ) : (
            <div className="overflow-hidden rounded-xl border border-slate-200">
              <table className="w-full border-collapse">
                <thead>
                  <tr>
                    <th className={HEAD}>Họ tên</th>
                    <th className={HEAD}>Tên đăng nhập</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {assignedStaff.map((staff) => (
                    <tr key={staff.id}>
                      <td className={`${CELL} font-semibold`}>{staff.fullName}</td>
                      <td className={CELL}>{staff.username}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* Nhân sự có thể phân công */}
        <section className="mt-5">
          <h4 id={availableHeadingId} className="text-sm font-bold text-[#142534] mb-2">
            {roleLabel} có thể phân công ({loading ? '…' : availableStaff.length})
            <span className="text-rose-500"> *</span>
          </h4>

          <label htmlFor={searchId} className="sr-only">
            Tìm {roleLabel.toLowerCase()}
          </label>
          <div className="relative mb-2">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              id={searchId}
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              maxLength={SEARCH_MAX_LENGTH}
              placeholder="Tìm theo họ tên, tên đăng nhập, phòng hiện tại"
              disabled={loading}
              className="w-full rounded-xl border border-slate-200 pl-8 pr-3 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-[#127e7a]/20 disabled:bg-slate-50"
            />
          </div>

          {loading ? (
            <p className="text-xs text-slate-500">Đang tải danh sách…</p>
          ) : availableStaff.length === 0 ? (
            <p className="text-xs text-amber-600">Không có nhân sự đang hoạt động phù hợp với loại phòng này.</p>
          ) : (
            <div className="max-h-64 overflow-y-auto rounded-xl border border-slate-200">
              <table className="w-full border-collapse" aria-labelledby={availableHeadingId}>
                <thead className="sticky top-0">
                  <tr>
                    <th className={`${HEAD} w-14 text-center`}>Chọn</th>
                    <th className={HEAD}>Họ tên</th>
                    <th className={HEAD}>Phòng hiện tại</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {visibleStaff.length === 0 ? (
                    <tr>
                      <td colSpan="3" className={`${CELL} text-center text-slate-400`}>
                        Không tìm thấy kết quả.
                      </td>
                    </tr>
                  ) : (
                    visibleStaff.map((staff) => {
                      const checked = selectedStaffIds.includes(staff.id);
                      const locked = isRoomLocked(staff);
                      return (
                        <tr
                          key={staff.id}
                          onClick={locked ? undefined : () => onToggle(staff.id)}
                          className={`transition-colors ${
                            locked
                              ? 'bg-slate-50 text-slate-400 cursor-not-allowed'
                              : checked
                                ? 'bg-teal-50 cursor-pointer'
                                : 'hover:bg-[#f9fbfb] cursor-pointer'
                          }`}
                        >
                          <td className={`${CELL} text-center`}>
                            <input
                              type="checkbox"
                              value={staff.id}
                              checked={checked}
                              disabled={locked}
                              onClick={(event) => event.stopPropagation()}
                              onChange={() => onToggle(staff.id)}
                              aria-label={`Chọn ${staff.fullName} (${staff.username})`}
                              className="accent-[#127e7a]"
                            />
                          </td>
                          <td className={CELL}>
                            <span className="font-semibold">{staff.fullName}</span>{' '}
                            <span className="text-slate-500">({staff.username})</span>
                          </td>
                          <td className={CELL}>
                            {staff.roomName ? (
                              [staff.roomCode, staff.roomName].filter(Boolean).join(' – ')
                            ) : (
                              <span className="text-slate-400">Chưa có phòng</span>
                            )}
                            {locked && (
                              <span className="block text-[11px] text-amber-600">
                                Còn ca làm việc từ hôm nay trở đi, chưa thể đổi phòng
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          )}
          <p className="mt-2 text-[11px] text-slate-500">
            Nếu nhân sự đang thuộc phòng khác, hệ thống sẽ chuyển họ sang phòng này. Nhân sự còn ca làm
            việc từ hôm nay trở đi phải được xóa các ca đó trước khi đổi phòng.
          </p>
        </section>

        <div className="mt-6 flex justify-end gap-3">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onClose}
            disabled={pending}
            className="text-xs font-semibold px-4"
          >
            Hủy
          </Button>
          <Button
            type="button"
            variant="mint"
            size="sm"
            onClick={confirm}
            disabled={pending || loading}
            className="text-xs font-semibold px-4"
          >
            {pending
              ? 'Đang phân phòng…'
              : selectedStaffIds.length > 0
                ? `Phân phòng (${selectedStaffIds.length})`
                : 'Phân phòng'}
          </Button>
        </div>
      </div>
    </div>
  );
}

export default RoomAssignmentDialog;
