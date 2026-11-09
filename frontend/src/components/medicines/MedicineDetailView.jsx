import { ArrowLeft, Pencil, Power, PowerOff } from 'lucide-react';
import { FormAlert } from '../shared/FormFields';
import { MEDICINE_CONTROL_GROUP_LABELS, MEDICINE_STATUS_LABELS } from '../../constants/medicineData';
import { FIELD_LABELS } from './medicineForm';

const STATUS_TONES = {
  ACTIVE: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]',
  INACTIVE: 'bg-rose-50 text-rose-600 border-rose-200',
};

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—';
}

/** UC-81 medicine detail, from which UC-83 (edit) and UC-84 (status) start. */
export function MedicineDetailView({ medicine, onGoToList, onEdit, onToggleStatus, statusBusy = false, statusError }) {
  const active = medicine.status === 'ACTIVE';
  const ToggleIcon = active ? PowerOff : Power;
  const rows = [
    [FIELD_LABELS.code, medicine.code],
    [FIELD_LABELS.registrationNumber, medicine.registrationNumber],
    [FIELD_LABELS.insuranceCode, medicine.insuranceCode ?? '—'],
    [FIELD_LABELS.activeIngredient, medicine.activeIngredient],
    [FIELD_LABELS.strength, medicine.strength],
    [FIELD_LABELS.route, medicine.route],
    [FIELD_LABELS.unit, medicine.unit],
    [FIELD_LABELS.controlGroup, MEDICINE_CONTROL_GROUP_LABELS[medicine.controlGroup] ?? medicine.controlGroup],
    ['Ngày tạo', formatDateTime(medicine.createdAt)],
    ['Cập nhật gần nhất', formatDateTime(medicine.updatedAt)],
  ];

  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-6 space-y-6">
      <button
        type="button"
        onClick={onGoToList}
        className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-[#127e7a] cursor-pointer"
      >
        <ArrowLeft className="w-3.5 h-3.5" />
        <span>Về danh mục thuốc</span>
      </button>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl! font-extrabold! text-[#1b2533]">{medicine.name}</h1>
          <span className={`inline-block mt-2 px-2 py-0.5 rounded-md text-[10px] font-semibold border ${STATUS_TONES[medicine.status]}`}>
            {MEDICINE_STATUS_LABELS[medicine.status]}
          </span>
        </div>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onEdit}
            className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
          >
            <Pencil className="w-3.5 h-3.5" />
            <span>Sửa thông tin</span>
          </button>
          <button
            type="button"
            onClick={onToggleStatus}
            disabled={statusBusy}
            className={`inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold rounded-xl cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed ${
              active
                ? 'text-rose-600 bg-rose-50 border border-rose-200 hover:bg-rose-100'
                : 'text-white bg-[#127e7a] hover:bg-[#0e6b67]'
            }`}
          >
            <ToggleIcon className="w-3.5 h-3.5" />
            <span>{active ? 'Ngừng sử dụng' : 'Kích hoạt'}</span>
          </button>
        </div>
      </div>

      {statusError && <FormAlert>{statusError}</FormAlert>}

      <dl className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs font-semibold text-slate-500">{label}</dt>
            <dd className="text-sm text-slate-800 mt-0.5 break-words">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export default MedicineDetailView;
