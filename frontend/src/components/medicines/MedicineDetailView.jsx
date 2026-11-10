import { CalendarClock, CalendarPlus, ChevronRight, FileText, FlaskConical, House, Pencil, Pill, Power, PowerOff } from 'lucide-react';
import { FormAlert } from '../shared/FormFields';
import { MEDICINE_CONTROL_GROUP_LABELS, MEDICINE_STATUS_LABELS } from '../../constants/medicineData';
import { FIELD_LABELS } from './medicineForm';

const STATUS_TONES = {
  ACTIVE: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]',
  INACTIVE: 'bg-rose-50 text-rose-600 border-rose-200',
};

const TIME_ZONE = 'Asia/Ho_Chi_Minh';

function DateStat({ icon: Icon, label, iso }) {
  return (
    <div className="flex items-start gap-3 px-6">
      <span className="w-11 h-11 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
        <Icon className="w-5 h-5" />
      </span>
      <div className="text-sm">
        <div className="text-xs text-slate-500">{label}</div>
        {iso ? (
          <>
            <div className="font-bold text-[#1b2533] mt-0.5">
              {new Date(iso).toLocaleDateString('vi-VN', { timeZone: TIME_ZONE })}
            </div>
            <div className="text-xs text-slate-500 mt-0.5">
              {new Date(iso).toLocaleTimeString('vi-VN', { timeZone: TIME_ZONE })}
            </div>
          </>
        ) : (
          <div className="font-semibold text-slate-400 mt-0.5">—</div>
        )}
      </div>
    </div>
  );
}

function InfoCard({ icon: Icon, title, rows }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4">
      <h2 className="flex items-center gap-3 px-4 py-3 rounded-xl bg-[#eef7f5] text-base! leading-6! font-bold! text-[#0f5f5c]">
        <Icon className="w-5 h-5 text-[#127e7a]" />
        {title}
      </h2>
      <dl className="mt-2 text-sm">
        {rows.map(([label, value]) => (
          <div
            key={label}
            className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-4 px-4 py-3 border-b border-slate-100 last:border-b-0 even:bg-slate-50/70"
          >
            <dt className="text-slate-500 font-medium">{label}</dt>
            <dd className="text-[#1b2533] break-words">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/** UC-81 medicine detail, from which UC-83 (edit) and UC-84 (status) start. */
export function MedicineDetailView({ medicine, onGoToList, onEdit, onToggleStatus, statusBusy = false, statusError }) {
  const active = medicine.status === 'ACTIVE';
  const ToggleIcon = active ? PowerOff : Power;
  const identityRows = [
    [FIELD_LABELS.code, <span className="font-semibold">{medicine.code}</span>],
    [FIELD_LABELS.name, medicine.name],
    [FIELD_LABELS.activeIngredient, medicine.activeIngredient],
    [FIELD_LABELS.registrationNumber, medicine.registrationNumber],
    [FIELD_LABELS.insuranceCode, medicine.insuranceCode || <span className="text-slate-400">—</span>],
  ];
  const usageRows = [
    [FIELD_LABELS.controlGroup, MEDICINE_CONTROL_GROUP_LABELS[medicine.controlGroup] ?? medicine.controlGroup],
    [FIELD_LABELS.strength, medicine.strength],
    [FIELD_LABELS.route, medicine.route],
    [FIELD_LABELS.unit, medicine.unit],
  ];

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-slate-500 flex-wrap">
        <House className="w-3.5 h-3.5" />
        <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
        <button type="button" onClick={onGoToList} className="hover:text-[#127e7a] cursor-pointer">Danh mục thuốc</button>
        <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
        <span className="text-[#0f5f5c] font-semibold">Chi tiết thuốc</span>
      </nav>

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 flex flex-col xl:flex-row xl:items-center gap-5">
        <div className="flex items-center gap-5 flex-1 min-w-0">
          <div className="w-20 h-20 rounded-2xl bg-[#eef7f5] text-[#127e7a] flex items-center justify-center shrink-0">
            <Pill className="w-9 h-9" />
          </div>
          <div className="min-w-0 border-l border-slate-100 pl-5">
            <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] truncate">{medicine.name}</h1>
            <div className="flex flex-wrap items-center gap-3 mt-2 text-sm text-slate-500">
              <span>{FIELD_LABELS.code}: {medicine.code}</span>
              <span className="h-4 w-px bg-slate-200" aria-hidden="true" />
              <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-lg text-xs font-semibold border ${STATUS_TONES[medicine.status]}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-[#1f9d68]' : 'bg-rose-500'}`} aria-hidden="true" />
                {MEDICINE_STATUS_LABELS[medicine.status]}
              </span>
            </div>
          </div>
        </div>

        <div className="flex divide-x divide-slate-100 xl:border-l xl:border-slate-100">
          <DateStat icon={CalendarPlus} label="Ngày tạo" iso={medicine.createdAt} />
          <DateStat icon={CalendarClock} label="Ngày cập nhật" iso={medicine.updatedAt} />
        </div>

        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={onEdit}
            className="min-h-11 inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold text-[#0f5f5c] bg-white border border-[#127e7a] rounded-xl hover:bg-[#eef7f5] transition-colors cursor-pointer"
          >
            <Pencil className="w-4 h-4" />
            <span>Sửa thông tin</span>
          </button>
          <button
            type="button"
            onClick={onToggleStatus}
            disabled={statusBusy}
            className={`min-h-11 inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold rounded-xl border transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed ${
              active
                ? 'text-rose-600 bg-rose-50 border-rose-300 hover:bg-rose-100'
                : 'text-white bg-[#127e7a] border-[#127e7a] hover:bg-[#0e6b67]'
            }`}
          >
            <ToggleIcon className="w-4 h-4" />
            <span>{active ? 'Ngừng sử dụng' : 'Kích hoạt'}</span>
          </button>
        </div>
      </section>

      {statusError && <FormAlert>{statusError}</FormAlert>}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-5 items-stretch">
        <InfoCard icon={FileText} title="Thông tin định danh" rows={identityRows} />
        <InfoCard icon={FlaskConical} title="Thông tin phân loại & sử dụng" rows={usageRows} />
      </div>
    </div>
  );
}

export default MedicineDetailView;
