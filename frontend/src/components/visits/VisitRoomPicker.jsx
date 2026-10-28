import { DoorOpen, MapPin, Clock, Stethoscope, RefreshCw } from 'lucide-react';
import { FieldError } from '../shared/FormFields';

function Count({ icon: Icon, label, value, tone }) {
  return (
    <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-lg text-[11px] font-semibold ${tone}`}>
      <Icon className="w-3.5 h-3.5" />
      {label}: {value}
    </span>
  );
}

/**
 * UC-40 steps 8-9 with UC-42: active examination rooms and today's queue counts.
 * TBD: the rooms endpoint returns no department name or assigned doctors yet, so
 * rooms without a doctor cannot be greyed out (UC-42 AF-01).
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
        className="grid grid-cols-1 md:grid-cols-2 2xl:grid-cols-3 gap-3"
      >
        {rooms.map((room) => {
          const selected = room.id === value;
          const roomCounts = counts[room.id] ?? { waiting: 0, inExamination: 0 };
          return (
            <label
              key={room.id}
              className={`relative flex items-start gap-3 p-4 rounded-xl border cursor-pointer transition-colors ${
                selected
                  ? 'border-[#127e7a] bg-[#f0f9f8] ring-2 ring-[#127e7a]/15'
                  : error
                    ? 'border-rose-200 bg-white hover:bg-slate-50'
                    : 'border-slate-200 bg-white hover:bg-slate-50'
              }`}
            >
              <input
                type="radio"
                name="visit-room"
                value={room.id}
                checked={selected}
                onChange={() => onChange(room.id)}
                className="mt-1 w-4 h-4 accent-[#127e7a] cursor-pointer"
              />
              <div className="min-w-0 flex-1 space-y-2">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-bold text-slate-800">{room.name}</span>
                    {room.id === suggestedRoomId && (
                      <span className="px-1.5 py-0.5 rounded-md text-[10px] font-semibold bg-[#f1edfd] text-[#7c5cdb]">
                        Phòng gợi ý
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-slate-400 font-mono">{room.code}</div>
                </div>
                <div className="space-y-1 text-xs text-slate-500">
                  {room.departmentName && (
                    <p className="flex items-center gap-1.5"><DoorOpen className="w-3.5 h-3.5" />{room.departmentName}</p>
                  )}
                  <p className="flex items-center gap-1.5"><MapPin className="w-3.5 h-3.5" />{room.location || '—'}</p>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <Count icon={Clock} label="Đang chờ" value={roomCounts.waiting} tone="bg-amber-50 text-amber-700" />
                  <Count icon={Stethoscope} label="Đang khám" value={roomCounts.inExamination} tone="bg-[#e8f0fe] text-[#3b6fd8]" />
                </div>
              </div>
            </label>
          );
        })}
      </div>
      {error && <FieldError id="visit-room-error">{error}</FieldError>}
    </div>
  );
}
