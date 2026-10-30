import { Sun, Sunset, Plus, Trash2, Edit2, DoorOpen, FileText } from 'lucide-react';

const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  DOCTOR: 'Bác sĩ',
  TECHNICIAN: 'Kỹ thuật viên',
  NURSE: 'Điều dưỡng',
  RECEPTIONIST: 'Tiếp đón',
  CASHIER: 'Thu ngân',
};

const ROLE_COLORS = {
  DOCTOR: 'bg-teal-50 text-teal-700 border-teal-200',
  TECHNICIAN: 'bg-purple-50 text-purple-700 border-purple-200',
  NURSE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  ADMIN: 'bg-blue-50 text-blue-700 border-blue-200',
};

function DutyCard({ item, canEdit, onEditDuty, onDeleteDuty, shift = 'MORNING' }) {
  const isMorning = shift === 'MORNING';
  const hoverBorder = isMorning ? 'hover:border-amber-300' : 'hover:border-sky-300';

  return (
    <div
      className={`group relative p-2 rounded-lg bg-white border border-slate-200/90 shadow-2xs hover:shadow-xs ${hoverBorder} transition-all`}
    >
      {/* Action buttons on hover (Admin only) */}
      {canEdit && (
        <div className="absolute top-1 right-1 flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity bg-white/95 rounded p-0.5 border border-slate-200 shadow-2xs z-10">
          <button
            type="button"
            onClick={() => onEditDuty(item)}
            className="p-0.5 text-slate-400 hover:text-teal-600 rounded hover:bg-slate-100 transition-colors"
            title="Chỉnh sửa ca trực"
          >
            <Edit2 className="w-3 h-3" />
          </button>
          <button
            type="button"
            onClick={() => onDeleteDuty(item)}
            className="p-0.5 text-slate-400 hover:text-rose-600 rounded hover:bg-slate-100 transition-colors"
            title="Xoá ca trực"
          >
            <Trash2 className="w-3 h-3" />
          </button>
        </div>
      )}

      {/* Staff Name - Full text, no ellipsis */}
      <div className="text-[11.5px] font-bold text-slate-800 leading-snug break-words">
        {item.staffName}
      </div>

      {/* Role Badge */}
      <div className="flex items-center gap-1 mt-1">
        <span
          className={`text-[9.5px] px-1.5 py-0.5 rounded border font-medium inline-block leading-tight ${
            ROLE_COLORS[item.staffRole] || 'bg-slate-50 text-slate-600 border-slate-200'
          }`}
        >
          {ROLE_LABELS[item.staffRole] || item.staffRole || 'Nhân viên'}
        </span>
      </div>

      {/* Assigned Room tag (Hiển thị mã phòng) */}
      {(item.roomCode || item.roomName) && (
        <div className="mt-1.5 pt-1 border-t border-slate-100 flex items-center gap-1 text-[10px] text-slate-600">
          <DoorOpen className="w-2.5 h-2.5 text-teal-600 shrink-0" />
          <span className="font-semibold text-slate-700 truncate" title={item.roomName || item.roomCode}>
            {item.roomCode || item.roomName}
          </span>
        </div>
      )}

      {/* Notes tag */}
      {item.notes && (
        <div className="mt-0.5 flex items-center gap-1 text-[9.5px] text-slate-400 italic truncate" title={item.notes}>
          <FileText className="w-2 h-2 shrink-0" />
          <span className="truncate">{item.notes}</span>
        </div>
      )}
    </div>
  );
}

export function DutyWeeklyMatrix({
  days = [],
  scheduleItems = [],
  canEdit = false,
  onAddDuty,
  onEditDuty,
  onDeleteDuty,
}) {
  const todayStr = new Date().toISOString().split('T')[0];

  const getCellItems = (dateStr, shift) => {
    return scheduleItems.filter(
      item => item.workDate === dateStr && item.shift === shift
    );
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
      {/* Table Container */}
      <div className="overflow-x-auto min-w-full">
        <table className="w-full border-collapse text-left table-fixed min-w-[700px]">
          {/* Column widths allocation (Total 100%) */}
          <colgroup>
            <col className="w-[10.4%]" />
            <col className="w-[12.8%]" />
            <col className="w-[12.8%]" />
            <col className="w-[12.8%]" />
            <col className="w-[12.8%]" />
            <col className="w-[12.8%]" />
            <col className="w-[12.8%]" />
            <col className="w-[12.8%]" />
          </colgroup>

          {/* Table Header: Days of Week */}
          <thead>
            <tr className="bg-slate-50/80 border-b border-slate-200">
              <th className="p-2 text-center text-[11px] font-bold text-slate-500 uppercase tracking-wider border-r border-slate-200">
                Ca làm việc
              </th>
              {days.map((day) => {
                const isToday = day.dateStr === todayStr;
                const isPast = day.dateStr < todayStr;
                return (
                  <th
                    key={day.dateStr}
                    className={`p-2 text-center border-r border-slate-200 last:border-r-0 ${
                      isToday ? 'bg-teal-50/60' : isPast ? 'bg-slate-100/40' : ''
                    }`}
                  >
                    <div className="flex flex-col items-center">
                      <span className={`text-[11px] font-bold ${isPast ? 'text-slate-500' : 'text-slate-700'}`}>
                        {day.label}
                      </span>
                      <span
                        className={`text-[11px] mt-0.5 px-1.5 py-0.2 rounded-full font-semibold ${
                          isToday
                            ? 'bg-teal-600 text-white shadow-xs'
                            : isPast
                            ? 'text-slate-400 bg-slate-200/50'
                            : 'text-slate-500 bg-slate-100'
                        }`}
                      >
                        {day.displayDate}
                      </span>
                      {isToday ? (
                        <span className="text-[9px] text-teal-700 font-bold uppercase tracking-wider mt-0.5">
                          Hôm nay
                        </span>
                      ) : isPast ? (
                        <span className="text-[9px] text-slate-400 font-medium mt-0.5">
                          Đã qua
                        </span>
                      ) : null}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>

          {/* Table Body: 2 Rows (Morning & Afternoon) */}
          <tbody className="divide-y divide-slate-200">
            {/* ROW 1: CA SÁNG */}
            <tr>
              <td className="p-2 bg-amber-50/30 border-r border-slate-200 align-middle text-center">
                <div className="flex flex-col items-center justify-center text-center py-2">
                  <div className="p-1.5 rounded-lg bg-amber-100 text-amber-700 mb-1">
                    <Sun className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-amber-900 leading-tight">Ca Sáng</span>
                  <span className="text-[10px] text-amber-700/90 font-medium mt-1 px-1.5 py-0.5 rounded bg-amber-100/60 leading-tight">
                    08:00 - 11:30
                  </span>
                </div>
              </td>

              {days.map((day) => {
                const items = getCellItems(day.dateStr, 'MORNING');
                const isToday = day.dateStr === todayStr;
                const isPast = day.dateStr < todayStr;
                const canEditCell = canEdit && !isPast;

                return (
                  <td
                    key={`morning-${day.dateStr}`}
                    className={`p-1.5 align-top border-r border-slate-200 last:border-r-0 transition-colors ${
                      isToday ? 'bg-teal-50/20' : isPast ? 'bg-slate-50/60' : 'hover:bg-slate-50/50'
                    }`}
                  >
                    <div className="space-y-1.5 min-h-[120px] flex flex-col justify-between">
                      <div className="space-y-1.5">
                        {items.length === 0 && !canEditCell && (
                          <div className="text-center py-5 text-[10.5px] text-slate-400 italic">
                            {isPast ? 'Không có lịch' : 'Chưa phân công'}
                          </div>
                        )}
                        {items.map((item) => (
                          <DutyCard
                            key={item.id}
                            item={item}
                            canEdit={canEditCell}
                            onEditDuty={onEditDuty}
                            onDeleteDuty={onDeleteDuty}
                            shift="MORNING"
                          />
                        ))}
                      </div>

                      {/* Quick Add Button (Admin only & Only for today or future) */}
                      {canEditCell && (
                        <button
                          type="button"
                          onClick={() => onAddDuty(day.dateStr, 'MORNING')}
                          className="w-full py-1 px-1 rounded-md border border-dashed border-slate-200 text-slate-500 hover:border-amber-400 hover:text-amber-800 hover:bg-amber-50/40 text-[10.5px] font-semibold flex items-center justify-center gap-1 transition-all mt-auto cursor-pointer"
                        >
                          <Plus className="w-2.5 h-2.5" />
                          <span className="truncate">Thêm người trực</span>
                        </button>
                      )}
                    </div>
                  </td>
                );
              })}
            </tr>

            {/* ROW 2: CA CHIỀU */}
            <tr>
              <td className="p-2 bg-sky-50/30 border-r border-slate-200 align-middle text-center">
                <div className="flex flex-col items-center justify-center text-center py-2">
                  <div className="p-1.5 rounded-lg bg-sky-100 text-sky-700 mb-1">
                    <Sunset className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-sky-900 leading-tight">Ca Chiều</span>
                  <span className="text-[10px] text-sky-700/90 font-medium mt-1 px-1.5 py-0.5 rounded bg-sky-100/60 leading-tight">
                    13:00 - 17:30
                  </span>
                </div>
              </td>

              {days.map((day) => {
                const items = getCellItems(day.dateStr, 'AFTERNOON');
                const isToday = day.dateStr === todayStr;
                const isPast = day.dateStr < todayStr;
                const canEditCell = canEdit && !isPast;

                return (
                  <td
                    key={`afternoon-${day.dateStr}`}
                    className={`p-1.5 align-top border-r border-slate-200 last:border-r-0 transition-colors ${
                      isToday ? 'bg-teal-50/20' : isPast ? 'bg-slate-50/60' : 'hover:bg-slate-50/50'
                    }`}
                  >
                    <div className="space-y-1.5 min-h-[120px] flex flex-col justify-between">
                      <div className="space-y-1.5">
                        {items.length === 0 && !canEditCell && (
                          <div className="text-center py-5 text-[10.5px] text-slate-400 italic">
                            {isPast ? 'Không có lịch' : 'Chưa phân công'}
                          </div>
                        )}
                        {items.map((item) => (
                          <DutyCard
                            key={item.id}
                            item={item}
                            canEdit={canEditCell}
                            onEditDuty={onEditDuty}
                            onDeleteDuty={onDeleteDuty}
                            shift="AFTERNOON"
                          />
                        ))}
                      </div>

                      {/* Quick Add Button (Admin only & Only for today or future) */}
                      {canEditCell && (
                        <button
                          type="button"
                          onClick={() => onAddDuty(day.dateStr, 'AFTERNOON')}
                          className="w-full py-1 px-1 rounded-md border border-dashed border-slate-200 text-slate-500 hover:border-sky-400 hover:text-sky-800 hover:bg-sky-50/40 text-[10.5px] font-semibold flex items-center justify-center gap-1 transition-all mt-auto cursor-pointer"
                        >
                          <Plus className="w-2.5 h-2.5" />
                          <span className="truncate">Thêm người trực</span>
                        </button>
                      )}
                    </div>
                  </td>
                );
              })}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default DutyWeeklyMatrix;
