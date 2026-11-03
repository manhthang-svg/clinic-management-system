import { RefreshCw } from 'lucide-react';
import { FieldError } from '../shared/FormFields';

/**
 * UC-40 steps 8-9 with UC-42: active examination rooms and today's queue counts.
 * The backend only returns active examination rooms that have an active assigned doctor.
 */
export function VisitRoomPicker({ rooms, counts, value, onChange, error, loading, loadError, onReload, suggestedRoomId }) {
  if (loading && rooms.length === 0) {
    return <p role="status" className="p-6 text-center text-sm text-slate-400">Đang tải danh sách phòng khám…</p>;
  }

  if (loadError || rooms.length === 0) {
    return (
      <div role="alert" className="p-6 rounded-xl border border-dashed border-slate-200 text-center space-y-3">
        <p className="text-sm text-slate-500">
          {loadError ?? 'Không có phòng khám nào đang hoạt động có thể tiếp nhận bệnh nhân. Vui lòng báo quản trị viên.'}
        </p>
        <button
          type="button"
          onClick={onReload}
          className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Tải lại
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div
        role="radiogroup"
        aria-label="Phòng khám"
        aria-invalid={Boolean(error)}
        aria-describedby={error ? 'visit-room-error' : undefined}
        className={`overflow-x-auto rounded-xl border ${error ? 'border-rose-200' : 'border-slate-100'}`}
      >
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-xs font-semibold text-[#1b2533]">
              <th className="pl-4 pr-2 py-3 w-14 text-center">Chọn</th>
              <th className="px-3 py-3">Phòng khám</th>
              <th className="px-3 py-3">Chuyên khoa</th>
              <th className="px-3 py-3">Vị trí</th>
              <th className="px-3 py-3">Bác sĩ trong ca</th>
              <th className="px-3 py-3 text-right">Số bệnh nhân đang chờ và khám</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm">
            {rooms.map((room) => {
              const selected = room.id === value;
              const roomCounts = counts[room.id] ?? { waiting: 0, inExamination: 0 };
              const inputId = `visit-room-${room.id}`;
              return (
                <tr
                  key={room.id}
                  onClick={() => onChange(room.id)}
                  className={`cursor-pointer transition-colors ${selected ? 'bg-[#f0f9f8]' : 'hover:bg-slate-50/70'}`}
                >
                  <td className="pl-4 pr-2 py-3 text-center">
                    <input
                      id={inputId}
                      type="radio"
                      name="visit-room"
                      value={room.id}
                      checked={selected}
                      onChange={() => onChange(room.id)}
                      onClick={(event) => event.stopPropagation()}
                      className="w-4 h-4 accent-[#127e7a] cursor-pointer align-middle"
                    />
                  </td>
                  <td className="px-3 py-3">
                    <label htmlFor={inputId} className="cursor-pointer">
                      <span className={`font-semibold ${selected ? 'text-[#127e7a]' : 'text-slate-800'}`}>
                        <span className="font-mono text-xs text-slate-500">{room.code}</span> – {room.name}
                      </span>
                      {room.id === suggestedRoomId && (
                        <span className="ml-2 px-1.5 py-0.5 rounded-md text-[10px] font-semibold bg-[#f1edfd] text-[#7c5cdb]">
                          Phòng gợi ý
                        </span>
                      )}
                    </label>
                  </td>
                  <td className="px-3 py-3 text-slate-600">{room.departmentName || '—'}</td>
                  <td className="px-3 py-3 text-slate-600">{room.location || '—'}</td>
                  <td className="px-3 py-3 text-slate-600">{room.doctorName || '—'}</td>
                  <td className="px-3 py-3 text-right">
                    <span className="font-semibold text-slate-800">{roomCounts.waiting + roomCounts.inExamination}</span>
                    <span className="block text-[11px] text-slate-400">
                      Chờ {roomCounts.waiting} · Đang khám {roomCounts.inExamination}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {error && <FieldError id="visit-room-error">{error}</FieldError>}
    </div>
  );
}
